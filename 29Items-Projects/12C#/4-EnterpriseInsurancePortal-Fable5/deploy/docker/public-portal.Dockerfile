# Build context: repository root
FROM node:20-alpine AS build
ARG VITE_API_BASE_URL=http://localhost:5101
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
WORKDIR /app
COPY src/public-portal/package*.json ./
RUN npm ci
COPY src/public-portal/ ./
RUN npm run build

FROM nginx:1.27-alpine AS runtime
COPY deploy/docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
