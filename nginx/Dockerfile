FROM nginx:1.25-alpine

# Install tools needed for acme.sh (DuckDNS DNS challenge)
# acme.sh is installed at runtime in entrypoint.sh
RUN apk add --no-cache \
    wget \
    curl \
    openssl \
    gettext \
    bash \
    socat

# Copy custom entrypoint
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Fallback HTTP-only config (used when SSL not yet available)
COPY nginx-init.conf /etc/nginx/nginx-init.conf

ENTRYPOINT ["/entrypoint.sh"]
