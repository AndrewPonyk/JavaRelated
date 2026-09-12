# Shared Shopping List

[![Flutter PR Quality Gate](https://github.com/household/shared-shopping-list/actions/workflows/flutter_ci.yml/badge.svg)](https://github.com/household/shared-shopping-list/actions)
[![Codemagic Build](https://img.shields.io/badge/Codemagic-Passing-brightgreen)](https://codemagic.io)

A real-time, collaborative grocery shopping application for families and roommates. Built with **Flutter**, **BLoC Clean Architecture**, and **Firebase** (Cloud Firestore, Firebase Auth, Cloud Functions, and App/Dynamic Links), featuring offline synchronization, supermarket store aisle route optimization, weekly staple replenishment templates, and automated CI/CD via **Codemagic** and **Firebase App Distribution**.

---

## 🌟 Key Features

- **Real-Time Multi-User Collaboration**: Instant sub-second updates across family members using Firestore snapshot listeners.
- **Optimistic "Got It" Checkoffs**: Zero-latency local checkoff animation with an automatic `PendingSyncIndicator` while mutations are synced to the cloud.
- **Supermarket Aisle Route Sorting**: Automatically re-orders shopping items following standard store layouts (Produce -> Bakery -> Meat -> Dairy -> Frozen -> Checkout) to speed up shopping trips.
- **Category Grouping & Filtering**: Filter chips for rapid grocery aisle navigation with iconography and color accents.
- **Weekly Staples & Templates**: One-tap restock for weekly household essentials ("Weekly Staples", "Weekend BBQ").
- **Dynamic Link Sharing**: Shareable invite links with cryptographic tokens and role-based permissions (`editor` vs `viewer`).
- **Offline-First Resilience**: Full offline caching with SQLite/LevelDB backing, syncing mutations as soon as network connectivity is restored.
- **Anonymous-First Onboarding & GitHub SSO**: Friction-free immediate use, with seamless in-app account linking to Email/Password or GitHub OAuth Single Sign-On.

---

## 🏗 System Architecture & Technology Stack

| Layer | Technology | Role |
| :--- | :--- | :--- |
| **Mobile Client** | Flutter 3.x, Dart 3.x | Cross-platform iOS & Android application |
| **State Management** | BLoC Pattern (`flutter_bloc`) | Predictable unidirectional event-to-state transitions |
| **Cloud Database** | Google Cloud Firestore | Real-time reactive document database with subcollections |
| **Security & RBAC** | Firestore Security Rules | Server-side validation, member authorization ($O(1)$ checks) |
| **Serverless Backend** | Firebase Cloud Functions (TypeScript) | Invitation token minting, redemption transactions & FCM push notifications |
| **CI/CD** | Codemagic & GitHub Actions | Automated lint, test, build, and deploy to Firebase App Distribution |
| **Local Emulator Stack** | Docker & Firebase CLI | Local full-stack sandbox with emulator UI dashboard |

For in-depth architectural specifications and Mermaid diagrams, see:
- [PROJECT-PLAN.md](docs/PROJECT-PLAN.md)
- [ARCHITECTURE.md](docs/ARCHITECTURE.md)
- [TECH-NOTES.md](docs/TECH-NOTES.md)
- [DATABASE-SCHEMA.md](docs/DATABASE-SCHEMA.md)

---

## 🚀 Quick Start

### Prerequisites
- [Flutter SDK](https://flutter.dev/docs/get-started/install) (`>= 3.24.0`)
- [Node.js](https://nodejs.org/) (`>= 20.0.0`)
- [Docker](https://www.docker.com/) & Docker Compose (optional for local backend emulator)

### 1. Clone & Configure Environment
```bash
git clone https://github.com/household/shared-shopping-list.git
cd shared-shopping-list

# Copy environment template
cp .env.example .env
```

### 2. Run Local Backend (Docker)
Start the complete Firebase backend emulator suite (Firestore, Auth, Functions, Emulator UI):
```bash
docker-compose up -d
```
The Firebase Emulator UI dashboard will be available at: **http://localhost:4000**
- Firestore Emulator: `localhost:8080`
- Auth Emulator: `localhost:9099`
- Functions Emulator: `localhost:5001`

### 3. Launch Flutter App
```bash
# Get dependencies
flutter pub get

# Run on connected device or emulator
flutter run
```

---

## 🌐 Cloud Functions API Reference

The backend exposes HTTPS callables and REST endpoints via Firebase Cloud Functions:

### 1. Health Check (`GET /healthCheck`)
- **Type**: HTTP Request
- **Response**: `200 OK`
```json
{
  "status": "healthy",
  "service": "shared-shopping-list-api",
  "timestamp": "2026-09-06T14:20:00.000Z",
  "uptimeSeconds": 142,
  "environment": "dev"
}
```

### 2. Create Invitation (`createInvitation`)
- **Type**: Callable HTTPS
- **Input**:
```json
{
  "listId": "list-001",
  "role": "editor",
  "expiresInDays": 7
}
```
- **Response**:
```json
{
  "token": "inv_k9x2m1_081a2f9b8c",
  "listId": "list-001",
  "inviteUrl": "https://shoppinglist.page.link/join?token=inv_k9x2m1_081a2f9b8c",
  "expiresAt": "2026-09-13T14:20:00.000Z"
}
```

### 3. Redeem Invitation (`redeemInvitation`)
- **Type**: Callable HTTPS
- **Input**:
```json
{
  "token": "inv_k9x2m1_081a2f9b8c"
}
```
- **Response**:
```json
{
  "success": true,
  "listId": "list-001",
  "listName": "Family Grocery Run",
  "role": "editor"
}
```

### 4. Apply Staples Template (`applyTemplateToList`)
- **Type**: Callable HTTPS
- **Input**:
```json
{
  "templateId": "template-weekly",
  "targetListId": "list-001"
}
```
- **Response**:
```json
{
  "success": true,
  "itemsAdded": 4,
  "listId": "list-001"
}
```

---

## 🧪 Testing & Code Quality

### Run Automated Flutter Test Suite (Unit, BLoC, Widget)
```bash
flutter test
```

### Run Static Analysis & Linter
```bash
flutter analyze
```

### Run Backend Cloud Functions Tests
```bash
cd firebase/functions
node --test test/invitation.test.js
```

---

## 📦 CI/CD & Deployment

- **Codemagic Pipeline** ([codemagic.yaml](codemagic.yaml)):
  - Triggers on push to `main` and version tags (`v*.*.*`).
  - Executes `flutter analyze`, `flutter test --coverage`, builds release Android APK / iOS IPA, and automatically deploys to **Firebase App Distribution** tester groups (`internal-qa`, `household-beta`).
- **GitHub Actions** ([.github/workflows/flutter_ci.yml](.github/workflows/flutter_ci.yml)):
  - Fast PR quality gate enforcing zero lint errors and passing unit tests.

---

## 🛠 Troubleshooting Guide

### 1. Emulator Port Conflicts (`4000`, `8080`, `9099`, `5001`)
- **Symptom**: `Port 8080 is already in use by another process`.
- **Solution**: Identify and kill the conflicting process or change the port in `firebase.json`:
  ```bash
  # Windows PowerShell
  Get-Process -Id (Get-NetTCPConnection -LocalPort 8080).OwningProcess | Stop-Process
  ```

### 2. Android Localhost Connection
- **Symptom**: Android emulator fails to connect to `localhost:8080` (connection refused).
- **Solution**: The Android emulator uses `10.0.2.2` as the alias to your host machine's loopback interface:
  ```dart
  final host = defaultTargetPlatform == TargetPlatform.android ? '10.0.2.2' : 'localhost';
  FirebaseFirestore.instance.useFirestoreEmulator(host, 8080);
  ```

### 3. Firestore Permission Denied (`permission-denied`)
- **Symptom**: `Missing or insufficient permissions` when adding or checking off items.
- **Solution**: Ensure your authenticated user's UID is included in the list's `memberUids` array. Review [`firebase/firestore.rules`](firebase/firestore.rules) to verify that item operations are performed by members.

### 4. Infinite Frame Timeout during Widget Tests
- **Symptom**: `pumpAndSettle timed out` in widget tests.
- **Solution**: Continuous animations (such as `CircularProgressIndicator` inside `PendingSyncIndicator`) keep the scheduler active. Always use `await tester.pump(duration)` instead of `pumpAndSettle()` when testing widgets with ongoing sync indicators.
