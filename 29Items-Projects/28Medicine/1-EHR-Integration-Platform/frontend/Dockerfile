# Serves the prebuilt SPA via nginx. Build the assets first: `npm run build`
# (produces frontend/dist) — keeps the image build fast and offline-friendly.
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK CMD wget -q -O /dev/null http://localhost:8080/ || exit 1
