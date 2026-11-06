# Production image: the static build served by nginx, which also proxies the API (same origin, ADR 0001).
FROM node:22.22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
# rendered with envsubst at start into /etc/nginx/conf.d (only the variables in NGINX_ENVSUBST_FILTER)
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/security-headers.conf.template /etc/nginx/templates/security-headers.inc.template
COPY --from=build /app/dist /usr/share/nginx/html
ENV BACKEND_URL=http://paygenix-app:8000 \
    MEDIA_ORIGIN="" \
    NGINX_ENVSUBST_FILTER="^(BACKEND_URL|MEDIA_ORIGIN)$" \
    NGINX_ENVSUBST_OUTPUT_DIR=/etc/nginx/conf.d
EXPOSE 80
