FROM nginx:1.25-alpine

# Install certbot + DuckDNS DNS plugin for SSL certificate issuance
# (DNS challenge — no port 80 needed, works behind Viettel residential ISP)
RUN apk add --no-cache python3 py3-pip py3-setuptools gettext \
    && pip3 install --break-system-packages certbot certbot-dns-duckdns 2>/dev/null \
    || pip3 install certbot certbot-dns-duckdns

# Copy custom entrypoint that handles SSL bootstrap via DNS challenge
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# nginx-init.conf is used as fallback when SSL is not yet available
COPY nginx-init.conf /etc/nginx/nginx-init.conf

# nginx.conf is the full SSL template; mounted via docker-compose volume
# at /etc/nginx/templates/default.conf.template so envsubst can expand ${DOMAIN}

ENTRYPOINT ["/entrypoint.sh"]
