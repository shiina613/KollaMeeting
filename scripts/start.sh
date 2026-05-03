#!/usr/bin/env bash
# =============================================================================
# scripts/start.sh — Kolla Meeting startup script (WSL2/bash)
# =============================================================================
# Khởi động toàn bộ stack Kolla Meeting với Cloudflare Tunnel.
# Chạy từ thư mục gốc của project (nơi có docker-compose.yml).
#
# Usage:
#   ./scripts/start.sh
#
# Steps:
#   1. check_docker()      — Kiểm tra Docker daemon đang chạy
#   2. start_services()    — docker compose up -d (trừ frontend)
#   3. get_tunnel_url()    — Poll log cloudflared, extract URL (timeout 30s)
#   4. update_env_file()   — Cập nhật .env với tunnel URL mới
#   5. build_frontend()    — docker compose up -d --build frontend
#   6. print_success()     — In tunnel URL ra màn hình
# =============================================================================

set -e

# =============================================================================
# Helper: extract_tunnel_url
# Dùng để extract URL từ một chuỗi log (sourceable cho testing).
# Input:  $1 = chuỗi log (có thể nhiều dòng)
# Output: URL qua stdout, hoặc rỗng nếu không tìm thấy
# =============================================================================
extract_tunnel_url() {
    local log_content="$1"
    echo "$log_content" | grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' | head -1
}

# =============================================================================
# Subtask 4.1 — check_docker()
# Kiểm tra Docker daemon đang sẵn sàng.
# Requirements: 11.2, 11.4
# =============================================================================
check_docker() {
    echo "🔍 Kiểm tra Docker daemon..."
    if ! docker info > /dev/null 2>&1; then
        echo "❌ Docker daemon không sẵn sàng. Hãy khởi động Docker Desktop."
        exit 1
    fi
    echo "✅ Docker daemon đang chạy."
}

# =============================================================================
# Subtask 4.2 — start_services()
# Khởi động tất cả services trừ frontend.
# Requirements: 11.2
# =============================================================================
start_services() {
    echo "🚀 Khởi động services (trừ frontend)..."
    if ! docker compose up -d --scale frontend=0; then
        echo "❌ Không thể khởi động services. Kiểm tra output ở trên."
        exit 1
    fi
    echo "✅ Services đã khởi động."
}

# =============================================================================
# Subtask 4.3 — get_tunnel_url()
# Poll log cloudflared để lấy tunnel URL, timeout 30 giây.
# Requirements: 2.1, 2.2, 2.3, 2.4
# =============================================================================
get_tunnel_url() {
    echo "⏳ Đang chờ cloudflared sinh URL (timeout 30s)..."
    local timeout=30
    local elapsed=0
    local tunnel_url=""

    while [ "$elapsed" -lt "$timeout" ]; do
        local logs
        logs=$(docker logs kolla-cloudflared 2>&1 || true)
        tunnel_url=$(extract_tunnel_url "$logs")

        if [ -n "$tunnel_url" ]; then
            echo "$tunnel_url"
            return 0
        fi

        sleep 2
        elapsed=$((elapsed + 2))
    done

    echo "❌ cloudflared không sinh URL sau 30s. Kiểm tra: docker logs kolla-cloudflared" >&2
    exit 1
}

# =============================================================================
# Subtask 4.5 — update_env_file()
# Cập nhật .env với tunnel URL mới.
# Requirements: 3.1, 3.2, 7.1, 7.2, 7.3
# =============================================================================
update_env_file() {
    local tunnel_url="$1"

    if [ ! -f ".env" ]; then
        echo "❌ .env không tìm thấy. Chạy: cp .env.example .env"
        exit 1
    fi

    echo "📝 Cập nhật .env với tunnel URL: $tunnel_url"

    # Derive URLs từ tunnel URL
    local api_url="${tunnel_url}/api/v1"
    local ws_url
    ws_url=$(echo "$tunnel_url" | sed 's|^https://|wss://|')/ws
    local cors_url="$tunnel_url"

    # Dùng sed -i.bak để portable giữa GNU sed (Linux) và BSD sed (macOS)
    sed -i.bak "s|^VITE_API_BASE_URL=.*|VITE_API_BASE_URL=${api_url}|" .env
    sed -i.bak "s|^VITE_WS_URL=.*|VITE_WS_URL=${ws_url}|" .env
    sed -i.bak "s|^CORS_ALLOWED_ORIGINS=.*|CORS_ALLOWED_ORIGINS=${cors_url}|" .env

    # Xóa file backup
    rm -f .env.bak

    echo "✅ .env đã được cập nhật."
}

# =============================================================================
# Subtask 4.7 — build_frontend()
# Rebuild và khởi động frontend container.
# Requirements: 3.3
# =============================================================================
build_frontend() {
    echo "🔨 Rebuild frontend container..."
    if ! docker compose up -d --build frontend; then
        echo "❌ Không thể build frontend. Kiểm tra output ở trên."
        exit 1
    fi
    echo "✅ Frontend đã được build và khởi động."
}

# =============================================================================
# Subtask 4.8 — print_success()
# In thông báo thành công kèm tunnel URL.
# Requirements: 3.4, 11.3
# =============================================================================
print_success() {
    local tunnel_url="$1"
    echo ""
    echo "✅ Kolla đang chạy tại: ${tunnel_url}"
    echo ""
}

# =============================================================================
# Main — Wire toàn bộ script
# Gọi các hàm theo thứ tự: check_docker → start_services → get_tunnel_url
#                          → update_env_file → build_frontend → print_success
# Requirements: 3.4, 11.2, 11.3
# =============================================================================
main() {
    check_docker
    start_services

    TUNNEL_URL=$(get_tunnel_url)

    update_env_file "$TUNNEL_URL"
    build_frontend
    print_success "$TUNNEL_URL"
}

main
