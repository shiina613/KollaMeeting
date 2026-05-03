#!/bin/sh
# nginx/entrypoint.sh
# Self-signed SSL certificate for IP-based deployment (no domain required).
# Falls back to HTTP-only if openssl fails.

set -e

DOMAIN="${DOMAIN:-localhost}"
CERT_DIR="/etc/letsencrypt/live/${DOMAIN}"
CERT_PATH="${CERT_DIR}/fullchain.pem"
KEY_PATH="${CERT_DIR}/privkey.pem"

echo "[entrypoint] DOMAIN=${DOMAIN}"

# ── Expand env vars in the nginx config template ──────────────────────────
mkdir -p /etc/nginx/conf.d
envsubst '${DOMAIN}' \
    < /etc/nginx/templates/default.conf.template \
    > /etc/nginx/conf.d/default.conf

echo "[entrypoint] nginx config expanded"

# ── Generate self-signed SSL certificate if not present ───────────────────
if [ ! -f "$CERT_PATH" ]; then
    echo "[entrypoint] Generating self-signed SSL certificate for ${DOMAIN}..."

    mkdir -p "${CERT_DIR}"

    # Generate self-signed cert valid for 3650 days (10 years)
    # SAN (Subject Alternative Name) required for modern browsers
    openssl req -x509 -nodes -newkey rsa:2048 \
        -keyout "${KEY_PATH}" \
        -out "${CERT_PATH}" \
        -days 3650 \
        -subj "/CN=${DOMAIN}/O=Kolla Meeting/C=VN" \
        -addext "subjectAltName=IP:${DOMAIN}" \
        2>/dev/null || \
    openssl req -x509 -nodes -newkey rsa:2048 \
        -keyout "${KEY_PATH}" \
        -out "${CERT_PATH}" \
        -days 3650 \
        -subj "/CN=${DOMAIN}/O=Kolla Meeting/C=VN" \
        2>/dev/null || {
            echo "[entrypoint] openssl failed. Starting without SSL (HTTP only)."
            exec nginx -c /etc/nginx/nginx-init.conf -g "daemon off;"
        }

    echo "[entrypoint] Self-signed certificate generated at ${CERT_DIR}"
fi

echo "[entrypoint] Starting nginx with SSL (self-signed)..."
exec nginx -g "daemon off;"
