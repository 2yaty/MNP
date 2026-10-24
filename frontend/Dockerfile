# syntax=docker/dockerfile:1

# --- Build: install from the lockfile and build the production bundle ---------------------------
FROM node:24-alpine AS build
WORKDIR /workspace

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY . .
RUN npx ng build

# --- Run: nginx serves the app and forwards /api to the backend ---------------------------------
FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/mnp-frontend/browser/ /usr/share/nginx/html/
EXPOSE 80
