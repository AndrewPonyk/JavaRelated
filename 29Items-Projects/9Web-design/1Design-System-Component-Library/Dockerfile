FROM node:20-alpine

WORKDIR /app
ENV NODE_ENV=development

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

EXPOSE 4100
CMD ["npm", "run", "api:dev"]
