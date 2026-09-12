# syntax=docker/dockerfile:1.7
FROM rust:1.85-bookworm AS wasm-builder
WORKDIR /workspace
RUN rustup target add wasm32-unknown-unknown \
    && cargo install wasm-pack --version 0.13.1 --locked
COPY Cargo.toml Cargo.lock* ./
COPY crates/image-processor ./crates/image-processor
RUN wasm-pack build crates/image-processor \
    --target web \
    --release \
    --out-dir ../../apps/web/public/wasm \
    --out-name image_processor

FROM node:22-bookworm-slim AS application-builder
WORKDIR /workspace
COPY package.json package-lock.json* ./
COPY apps/web/package.json ./apps/web/package.json
RUN npm ci --ignore-scripts
COPY apps/web ./apps/web
COPY --from=wasm-builder /workspace/apps/web/public/wasm ./apps/web/public/wasm
ARG VITE_ENABLE_PRESET_API=true
ARG VITE_API_BASE_URL=
ENV VITE_ENABLE_PRESET_API=$VITE_ENABLE_PRESET_API
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build:web && npm run build:api --workspace @webassembly-image-editor/web

FROM node:22-bookworm-slim AS api-runtime
WORKDIR /app
ENV NODE_ENV=production
ENV NODE_OPTIONS=--enable-source-maps
COPY --from=application-builder /workspace/apps/web/dist-server/standalone.mjs ./standalone.mjs
COPY --from=application-builder /workspace/apps/web/dist-server/standalone.mjs.map ./standalone.mjs.map
USER node
EXPOSE 3000
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
    CMD node -e "fetch('http://127.0.0.1:3000/api/health').then(r=>{if(!r.ok)process.exit(1)}).catch(()=>process.exit(1))"
CMD ["node", "standalone.mjs"]

FROM nginxinc/nginx-unprivileged:1.30-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=application-builder /workspace/apps/web/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
