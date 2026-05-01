#!/bin/sh
# nginx/entrypoint.sh
# Uses acme.sh with DuckDNS DNS challenge for SSL certificate issuance.
# acme.sh has native DuckDNS support via dns_duckdns hook.

set -e

DOMAIN="${DOMAIN:-kolla.local}"
DUCKDNS_TOKEN="${DUCKDNS_TOKEN:-}"
CERTBOT_EMAIL="${CERTBOT_EMAIL:-admin@example.com}"
CERT_PATH="/etc/letsencrypt/live/${DOMAIN}/fullchain.pem"
ACME_HOME="/root/.acme.sh"

echo "[entrypoint] DOMAIN=${DOMAIN}"

# ── Expand env vars in the nginx config template ──────────────────────────
mkdir -p /etc/nginx/conf.d
envsubst '${DOMAIN}' \
    < /etc/nginx/templates/default.conf.template \
    > /etc/nginx/conf.d/default.conf

echo "[entrypoint] nginx config expanded"

# ── Obtain SSL certificate if not present ─────────────────────────────────
if [ ! -f "$CERT_PATH" ]; then
    echo "[entrypoint] SSL certificate not found. Obtaining via acme.sh + DuckDNS..."

    if [ -z "$DUCKDNS_TOKEN" ]; then
        echo "[entrypoint] ERROR: DUCKDNS_TOKEN not set. Starting without SSL."
        exec nginx -c /etc/nginx/nginx-init.conf -g "daemon off;"
    fi

    # Install acme.sh if not present
    if [ ! -f "${ACME_HOME}/acme.sh" ]; then
        echo "[entrypoint] Installing acme.sh..."
        wget -qO- https://get.acme.sh | sh -s email="${CERTBOT_EMAIL}" || {
            echo "[entrypoint] acme.sh install failed. Starting without SSL."
            exec nginx -c /etc/nginx/nginx-init.conf -g "daemon off;"
        }
    fi

    # Export DuckDNS token for acme.sh dns_duckdns hook
    export DuckDNS_Token="${DUCKDNS_TOKEN}"

    echo "[entrypoint] Requesting certificate for ${DOMAIN}..."
    "${ACME_HOME}/acme.sh" --issue \
        --dns dns_duckdns \
        -d "${DOMAIN}" \
        --server letsencrypt \
        --force \
        || {
            echo "[entrypoint] acme.sh failed. Starting without SSL."
            exec nginx -c /etc/nginx/nginx-init.conf -g "daemon off;"
        }

    # Install cert to /etc/letsencrypt/live/${DOMAIN}/
    mkdir -p "/etc/letsencrypt/live/${DOMAIN}"
    "${ACME_HOME}/acme.sh" --install-cert \
        -d "${DOMAIN}" \
        --cert-file     "/etc/letsencrypt/live/${DOMAIN}/cert.pem" \
        --key-file      "/etc/letsencrypt/live/${DOMAIN}/privkey.pem" \
        --fullchain-file "/etc/letsencrypt/live/${DOMAIN}/fullchain.pem" \
        || {
            echo "[entrypoint] cert install failed. Starting without SSL."
            exec nginx -c /etc/nginx/nginx-init.conf -g "daemon off;"
        }

    echo "[entrypoint] SSL certificate obtained successfully."
fi

echo "[entrypoint] Starting nginx with SSL..."
exec nginx -g "daemon off;"
