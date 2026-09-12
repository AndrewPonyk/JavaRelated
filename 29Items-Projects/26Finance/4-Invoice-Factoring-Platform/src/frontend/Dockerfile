# ---- build ----
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
# VITE_* build args are baked into the static bundle at build time.
ARG VITE_API_BASE_URL=/
ARG VITE_STRIPE_PUBLISHABLE_KEY=
ARG VITE_PLAID_ENV=sandbox
RUN npm run build

# ---- runtime (static files served by nginx) ----
FROM nginx:1.27-alpine AS final
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK CMD wget -qO- http://localhost:8080/ >/dev/null 2>&1 || exit 1
