# ---- Build stage ----
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
# Use `npm install` (no committed lockfile). Commit package-lock.json and
# switch to `npm ci` for reproducible builds.
RUN npm install
COPY . .
RUN npm run build

# ---- Serve stage (static assets via nginx) ----
FROM nginx:1.27-alpine AS runtime
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
