# Caddy with the frontend build (T-E0-06). Build context: repository root.
FROM node:24-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json frontend/.npmrc ./
RUN npm ci
COPY frontend/ ./
# RNF-25: analytics ids are build-time values (empty: no analytics and no consent banner)
ARG VITE_GA4_MEASUREMENT_ID=
ARG VITE_META_PIXEL_ID=
RUN VITE_GA4_MEASUREMENT_ID="$VITE_GA4_MEASUREMENT_ID" VITE_META_PIXEL_ID="$VITE_META_PIXEL_ID" npm run build

FROM caddy:2.11.7-alpine
COPY deploy/caddy/Caddyfile /etc/caddy/Caddyfile
COPY --from=frontend /frontend/dist/ /srv/www/
