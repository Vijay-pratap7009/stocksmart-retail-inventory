# syntax=docker/dockerfile:1
# StockSmart frontend — Angular 17 production build served by Nginx.
# Stage 1 installs strictly from the lockfile and builds the app; stage 2
# serves the static bundle and reverse-proxies /api/* to the backend.
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
# Upstream for the /api proxy (rendered into nginx.conf at container start).
# Compose/Docker run may override; keep it an internal service name only.
ENV BACKEND_URL=backend:8080
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://localhost/ || exit 1
