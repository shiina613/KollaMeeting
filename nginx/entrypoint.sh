#!/bin/sh
# nginx/entrypoint.sh
# Custom entrypoint for the nginx container.
#
# Behaviour:
#   1. If SSL certs do NOT exist → start nginx in HTTP-only init mode so
#      certbot can complete the ACME challenge, then wait for the cert to
#      appear (the certbot container handles the actual issuance).
#   2. Once certs exist → (re)start nginx with the full SSL config.
#
# The nginx.conf template uses ${DOMAIN} placeholders; envsubst expands
# them before nginx loads the config.

set -e

DOMAIN="${DOMAIN:-kolla.local}"
CERT_PATH="/etc/letsencrypt/live/${DOMAIN}/fullchain.pem"

echo "[entrypoint] DOMAIN=${DOMAIN}"

# ── Expand env vars in the nginx config template ──────────────────────────
# The template is mounted at /etc/nginx/templates/default.conf.template
# and written to /etc/nginx/conf.d/default.conf
mkdir -p /etc/nginx/conf.d
envsubst '${DOMAIN}' \
    < /etc/nginx/templates/default.conf.template \
    > /etc/nginx/conf.d/default.conf

echo "[entrypoint] nginx config template expanded to /etc/nginx/conf.d/default.conf"

# ── Check whether SSL certs already exist ─────────────────────────────────
if [ ! -f "$CERT_PATH" ]; then
    echo "[entrypoint] SSL certificate not found at ${CERT_PATH}."
    echo "[entrypoint] Starting nginx in HTTP-only init mode for ACME challenge..."

    # Start nginx with the minimal init config (no SSL required)
    nginx -c /etc/nginx/nginx-init.conf -g "daemon off;" &
    NGINX_PID=$!

    echo "[entrypoint] nginx (init mode) started with PID ${NGINX_PID}."
    echo "[entrypoint] Waiting for certbot to obtain certificates..."

    # Poll until the certificate file appears.
    # The certbot container runs independently and writes certs to the
    # shared certbot-certs volume.
    while [ ! -f "$CERT_PATH" ]; do
        sleep 5
        echo "[entrypoint] Still waiting for ${CERT_PATH} ..."
    done

    echo "[entrypoint] Certificate found. Stopping init nginx (PID ${NGINX_PID})..."
    kill "$NGINX_PID"
    wait "$NGINX_PID" 2>/dev/null || true
    echo "[entrypoint] Init nginx stopped."
fi

echo "[entrypoint] Starting nginx with full SSL configuration..."
exec nginx -g "daemon off;"
