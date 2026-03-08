# Base image
FROM node:18-alpine AS build-stage

# Set working directory
WORKDIR /app

# Copy package files (stub)
COPY package*.json ./

# Install dependencies
RUN npm install

# Copy project files
COPY . .

# Build the app for production
RUN npm run build

# Production stage
FROM nginx:stable-alpine AS production-stage

# Copy built assets to Nginx serving directory
COPY --from=build-stage /app/dist /usr/share/nginx/html

# Copy custom Nginx configuration if needed
# COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
