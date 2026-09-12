# Multi-stage Dockerfile for Firebase Emulators Suite
FROM node:20-alpine

# Install Java JRE (required by Firebase Firestore emulator) and bash
RUN apk add --no-cache openjdk17-jre bash curl

# Install Firebase CLI globally
RUN npm install -g firebase-tools

WORKDIR /app

# Copy Firebase project definitions
COPY firebase.json ./
COPY firebase/ ./firebase/

# Install functions dependencies if needed
WORKDIR /app/firebase/functions
RUN if [ -f package.json ]; then npm install --omit=dev; fi

WORKDIR /app

# Expose ports for Firestore (8080), Auth (9099), Functions (5001), UI (4000)
EXPOSE 8080 9099 5001 4000

# Start Firebase Emulators
CMD ["firebase", "emulators:start", "--project", "shopping-list-dev"]
