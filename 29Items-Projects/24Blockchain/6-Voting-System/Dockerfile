FROM node:18-alpine AS builder

WORKDIR /app
COPY package.json ./
RUN npm install --production=false
COPY . .
RUN npm run build

FROM node:18-alpine AS production

WORKDIR /app
ENV NODE_ENV=production

COPY package.json ./
RUN npm install --production && npm cache clean --force

# Copy backend source
COPY src/api/ src/api/
COPY config/ config/

# Copy frontend build for optional static serving
COPY --from=builder /app/build public/

EXPOSE 3001
USER node
CMD ["node", "src/api/server.js"]
