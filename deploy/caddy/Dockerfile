# Caddy with the frontend build (T-E0-06). Build context: repository root.
FROM node:24-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json frontend/.npmrc ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM caddy:2.11.7-alpine
COPY deploy/caddy/Caddyfile /etc/caddy/Caddyfile
COPY --from=frontend /frontend/dist/ /srv/www/
