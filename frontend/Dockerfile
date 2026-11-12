# syntax=docker/dockerfile:1

# ---- Build ----
FROM node:26-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---- Serve ----
# Runs as a non-root user and listens on 8080.
FROM nginxinc/nginx-unprivileged:1.30-alpine
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 8080
