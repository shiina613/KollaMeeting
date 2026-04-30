FROM nginx:1.25-alpine

# Copy custom entrypoint that handles SSL bootstrap
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# nginx-init.conf is used during the initial ACME challenge (no SSL yet)
COPY nginx-init.conf /etc/nginx/nginx-init.conf

# nginx.conf is the full SSL template; mounted via docker-compose volume
# at /etc/nginx/templates/default.conf.template so envsubst can expand ${DOMAIN}

ENTRYPOINT ["/entrypoint.sh"]
