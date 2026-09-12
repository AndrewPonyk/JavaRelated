# Build the SPA, then serve static assets from nginx.
FROM node:20-slim AS builder
WORKDIR /app
COPY package.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:1.27-alpine AS runtime
COPY --from=builder /app/dist /usr/share/nginx/html
# TODO: add an nginx.conf that proxies /api to the backend service in-cluster.
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
