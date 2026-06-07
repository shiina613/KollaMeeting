#!/usr/bin/env bash
set -euo pipefail

# One-command demo start for KollaMeeting on WSL2/Linux.
# Starts the Docker Compose stack with Cloudflare Quick Tunnel and prints the
# final public URL. Jitsi media is external; this stack only runs KollaMeeting.

extract_tunnel_url() {
    local log_content="${1:-}"
    printf "%s" "$log_content" | grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' | tail -1 || true
}

check_docker() {
    echo "[*] Checking Docker daemon..."
    if ! docker info >/dev/null 2>&1; then
        echo "[ERROR] Docker daemon is not ready. Start Docker Desktop first."
        exit 1
    fi
    echo "[OK] Docker daemon is ready."
}

set_env_value() {
    local key="$1"
    local value="$2"
    if grep -qE "^${key}=" .env; then
        local escaped
        escaped="$(printf "%s" "$value" | sed -e 's/[\/&]/\\&/g')"
        sed -i.bak "s|^${key}=.*|${key}=${escaped}|" .env
    else
        printf "\n%s=%s\n" "$key" "$value" >> .env
    fi
    rm -f .env.bak
}

get_env_value() {
    local key="$1"
    grep -E "^${key}=" .env 2>/dev/null | tail -1 | cut -d= -f2- || true
}

random_base64() {
    local bytes="$1"
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -base64 "$bytes"
    else
        head -c "$bytes" /dev/urandom | base64 | tr -d '\n'
    fi
}

random_hex() {
    local bytes="$1"
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -hex "$bytes"
    else
        od -An -tx1 -N "$bytes" /dev/urandom | tr -d ' \n'
    fi
}

ensure_env_file() {
    if [ ! -f ".env" ]; then
        echo "[*] .env not found; creating it from .env.example..."
        cp .env.example .env
        echo "[OK] .env created."
    fi

    local default_jwt="Y2hhbmdlbWUtc3VwZXItc2VjcmV0LWtleS1hdC1sZWFzdC0zMi1jaGFycw=="
    local jwt_secret
    jwt_secret="$(get_env_value JWT_SECRET)"
    if [ -z "$jwt_secret" ] || [ "$jwt_secret" = "$default_jwt" ]; then
        set_env_value JWT_SECRET "$(random_base64 32)"
        echo "[OK] JWT_SECRET generated."
    fi

    local callback_key
    callback_key="$(get_env_value ASR_CALLBACK_API_KEY)"
    if [ -z "$callback_key" ] || [ "$callback_key" = "internal-callback-key-change-me" ]; then
        set_env_value ASR_CALLBACK_API_KEY "$(random_hex 24)"
        echo "[OK] ASR_CALLBACK_API_KEY generated."
    fi

    set_env_value DIGITAL_SIGNATURE_ENABLED "true"
    set_env_value DIGITAL_SIGNATURE_KEYSTORE_PATH "/app/keys/signing.p12"
    set_env_value DIGITAL_SIGNATURE_KEYSTORE_PASSWORD "kolla-signing-dev"
    set_env_value DIGITAL_SIGNATURE_KEYSTORE_TYPE "PKCS12"

    if [ ! -f "keys/signing.p12" ]; then
        echo "[*] Creating demo signing keystore..."
        ./scripts/generate-signing-keystore.sh
    fi
}

start_services() {
    echo "[*] Building backend image..."
    docker compose build backend

    echo "[*] Building asr-service image..."
    docker compose build asr-service

    echo "[*] Starting stack without frontend..."
    docker compose rm -sf cloudflared >/dev/null 2>&1 || true
    docker compose up -d --scale frontend=0
    echo "[OK] Base services started."
}

get_tunnel_url() {
    echo "[*] Waiting for Cloudflare Quick Tunnel URL..."
    local timeout=30
    local elapsed=0
    local tunnel_url=""

    while [ "$elapsed" -lt "$timeout" ]; do
        local logs
        logs="$(docker logs kolla-cloudflared 2>&1 || true)"
        tunnel_url="$(extract_tunnel_url "$logs")"
        if [ -n "$tunnel_url" ]; then
            printf "%s" "$tunnel_url"
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done

    echo "[ERROR] cloudflared did not print a Quick Tunnel URL within 30s." >&2
    echo "        Check: docker logs kolla-cloudflared" >&2
    exit 1
}

update_env_file() {
    local tunnel_url="$1"

    if [ ! -f ".env" ]; then
        echo "[ERROR] .env not found after automatic creation step."
        exit 1
    fi

    echo "[*] Updating .env with tunnel URL: ${tunnel_url}"
    local api_url="${tunnel_url}/api/v1"
    local ws_url
    ws_url="$(printf "%s" "$tunnel_url" | sed 's|^https://|wss://|')/ws"

    sed -i.bak "s|^VITE_API_BASE_URL=.*|VITE_API_BASE_URL=${api_url}|" .env
    sed -i.bak "s|^VITE_WS_URL=.*|VITE_WS_URL=${ws_url}|" .env
    sed -i.bak "s|^CORS_ALLOWED_ORIGINS=.*|CORS_ALLOWED_ORIGINS=${tunnel_url}|" .env
    rm -f .env.bak
    echo "[OK] .env updated."
}

wait_backend() {
    echo "[*] Waiting for backend health..."
    local timeout=120
    local elapsed=0

    while [ "$elapsed" -lt "$timeout" ]; do
        local status
        status="$(docker inspect kolla-backend --format='{{.State.Health.Status}}' 2>/dev/null || true)"
        if [ "$status" = "healthy" ]; then
            echo "[OK] Backend is healthy."
            return 0
        fi
        echo "    Backend status: ${status:-unknown} (${elapsed}/${timeout}s)"
        sleep 3
        elapsed=$((elapsed + 3))
    done

    echo "[WARN] Backend was not healthy after 120s; continuing so logs can be inspected."
}

build_frontend() {
    echo "[*] Rebuilding frontend with current VITE_* URLs..."
    docker compose build --no-cache frontend
    docker compose up -d frontend
    docker compose restart nginx >/dev/null 2>&1 || true
    echo "[OK] Frontend started and nginx refreshed."
}

print_success() {
    local tunnel_url="$1"
    echo ""
    echo ">>> Kolla is running at: ${tunnel_url}"
    echo ""
}

main() {
    check_docker
    ensure_env_file
    start_services
    local tunnel_url
    tunnel_url="$(get_tunnel_url)"
    update_env_file "$tunnel_url"
    wait_backend
    build_frontend
    print_success "$tunnel_url"
}

main "$@"
