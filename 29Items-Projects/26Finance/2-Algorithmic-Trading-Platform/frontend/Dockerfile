# Build the Vite bundle, then serve it from nginx (which also proxies /api).
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
# Use relative API base so nginx can proxy to the gateway.
ENV VITE_API_BASE_URL=/api/v1
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
