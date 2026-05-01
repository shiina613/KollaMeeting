#!/bin/sh
# nginx/entrypoint.sh
# Custom entrypoint for the nginx container.
#
# SSL Strategy:
#   - Uses certbot DNS challenge via DuckDNS plugin (no port 80 needed).
#   - If certs don't exist, runs certbot --dns-duckdns to obtain them.
#   - Once certs exist, starts nginx with full SSL config.
#   - DOMAIN, DUCKDNS_TOKEN, CERTBOT_EMAIL must be set via environment.

set -e

DOMAIN="${DOMAIN:-kolla.local}"
DUCKDNS_TOKEN="${DUCKDNS_TOKEN:-}"
CERTBOT_EMAIL="${CERTBOT_EMAIL:-admin@example.com}"
CERT_PATH="/etc/letsencrypt/live/${DOMAIN}/fullchain.pem"

echo "[entrypoint] DOMAIN=${DOMAIN}"

# ── Expand env vars in the nginx config template ──────────────────────────
mkdir -p /etc/nginx/conf.d
envsubst '${DOMAIN}' \
    < /etc/nginx/templates/default.conf.template \
    > /etc/nginx/conf.d/default.conf

echo "[entrypoint] nginx config expanded to /etc/nginx/conf.d/default.conf"

# ── Obtain SSL certificate if not present ─────────────────────────────────
if [ ! -f "$CERT_PATH" ]; then
    echo "[entrypoint] SSL certificate not found. Obtaining via DNS challenge..."

    if [ -z "$DUCKDNS_TOKEN" ]; then
        echo "[entrypoint] ERROR: DUCKDNS_TOKEN is not set. Cannot obtain SSL cert."
        echo "[entrypoint] Starting nginx without SSL (HTTP only on port 8080)..."
        # Fallback: start with init config so the app is at least reachable internally
        exec nginx -c /etc/nginx/nginx-init.conf -g "daemon off;"
    fi

    # Install certbot DuckDNS plugin if not present
    if ! certbot plugins 2>/dev/null | grep -q dns-duckdns; then
        echo "[entrypoint] Installing certbot-dns-duckdns plugin..."
        pip install certbot-dns-duckdns --quiet 2>/dev/null || \
        pip3 install certbot-dns-duckdns --quiet 2>/dev/null || true
    fi

    # Write DuckDNS credentials file
    mkdir -p /etc/letsencrypt
    cat > /tmp/duckdns.ini << EOF
dns_duckdns_token = ${DUCKDNS_TOKEN}
EOF
    chmod 600 /tmp/duckdns.ini

    echo "[entrypoint] Running certbot DNS challenge for ${DOMAIN}..."
    certbot certonly \
        --non-interactive \
        --agree-tos \
        --email "${CERTBOT_EMAIL}" \
        --authenticator dns-duckdns \
        --dns-duckdns-token "${DUCKDNS_TOKEN}" \
        --dns-duckdns-propagation-seconds 60 \
        -d "${DOMAIN}" \
        || {
            echo "[entrypoint] WARNING: certbot failed. Starting nginx without SSL."
            exec nginx -c /etc/nginx/nginx-init.conf -g "daemon off;"
        }

    echo "[entrypoint] SSL certificate obtained successfully."
fi

echo "[entrypoint] Starting nginx with full SSL configuration..."
exec nginx -g "daemon off;"
