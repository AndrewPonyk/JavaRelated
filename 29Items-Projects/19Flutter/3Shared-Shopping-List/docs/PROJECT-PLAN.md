# Project Plan: Shared Shopping List

A collaborative, real-time grocery shopping and household pantry application built with **Flutter**, **BLoC**, and **Firebase** (Firestore, Firebase Auth, Firebase Cloud Functions, and Dynamic/App Links), featuring offline synchronization, store route optimization, and automated CI/CD with **Codemagic** and **Firebase App Distribution**.

---

## 1.1 Project File Structure (Code + CI + Tools)

The project adheres to **Feature-First Clean Architecture** principles adapted for Flutter and BLoC. This guarantees clear boundaries between presentation, domain business rules, and data access layers, while keeping features modular and independently testable.

```text
shared_shopping_list/
├── .github/
│   └── workflows/
│       ├── flutter_ci.yml                # Fast pull-request validation (lint, format, test)
│       └── release_tag.yml               # Automated semantic release tag pipeline
├── android/                              # Native Android platform wrapper & Gradle configs
├── ios/                                  # Native iOS platform wrapper & CocoaPods / Xcode workspace
├── docs/                                 # Architectural and operational documentation
│   ├── PROJECT-PLAN.md                   # Delivery plan, file structure & implementation TODOs
│   ├── ARCHITECTURE.md                  # Comprehensive system architecture & Mermaid diagrams
│   ├── TECH-NOTES.md                     # CI/CD, testing, deployment & gotchas
│   └── DATABASE-SCHEMA.md                # Firestore collections, indexing & security rules spec
├── firebase/                             # Firebase backend, rules, indexes & serverless functions
│   ├── firestore.rules                   # Granular RBAC and data validation security rules
│   ├── firestore.indexes.json            # Composite indexing for queries & orderings
│   └── functions/                        # Serverless Node.js / TypeScript Cloud Functions
│       ├── src/
│       │   ├── controllers/
│       │   │   └── invitation.controller.ts  # Member invitation token validation & redemption
│       │   ├── services/
│       │   │   ├── notification.service.ts   # FCM push notification dispatcher for list updates
│       │   │   └── staple_template.service.ts # Routine staple replenishment cron engine
│       │   └── index.ts                  # Cloud Functions export entry point
│       ├── package.json
│       ├── tsconfig.json
│       └── .eslintrc.js
├── lib/
│   ├── main.dart                         # Application entrypoint, DI initialization & App widget
│   ├── core/                             # Cross-cutting concerns and shared primitives
│   │   ├── constants/
│   │   │   ├── app_constants.dart        # Route names, collection names, timeouts
│   │   │   ├── store_sections.dart       # Predefined supermarket aisle orderings
│   │   │   └── item_categories.dart      # Standard grocery categorization & icons
│   │   ├── errors/
│   │   │   ├── exceptions.dart           # Low-level platform & network exceptions
│   │   │   └── failures.dart             # Domain-level failure types (Equatable)
│   │   ├── network/
│   │   │   └── network_info.dart         # Connectivity checker & offline status watcher
│   │   ├── theme/
│   │   │   ├── app_colors.dart           # Harmonious palette & dark/light theme tokens
│   │   │   ├── app_theme.dart            # Material 3 ThemeData with custom extensions
│   │   │   └── app_typography.dart       # Modern type scale
│   │   └── utils/
│   │       ├── bloc_observer.dart        # Global BLoC state transition logging
│   │       └── currency_formatter.dart   # Regional price estimate formatting
│   ├── features/
│   │   ├── auth/                         # Authentication & Household Account management
│   │   │   ├── data/
│   │   │   │   ├── datasources/
│   │   │   │   │   └── auth_remote_datasource.dart
│   │   │   │   ├── models/
│   │   │   │   │   └── user_model.dart
│   │   │   │   └── repositories/
│   │   │   │       └── auth_repository_impl.dart
│   │   │   ├── domain/
│   │   │   │   ├── entities/
│   │   │   │   │   └── user_profile.dart
│   │   │   │   ├── repositories/
│   │   │   │   │   └── auth_repository.dart
│   │   │   │   └── usecases/
│   │   │   │       ├── sign_in_with_google.dart
│   │   │   │       ├── link_anonymous_account.dart
│   │   │   │       └── get_current_user.dart
│   │   │   └── presentation/
│   │   │       ├── bloc/
│   │   │       │   ├── auth_bloc.dart
│   │   │       │   ├── auth_event.dart
│   │   │       │   └── auth_state.dart
│   │   │       └── screens/
│   │   │           ├── login_screen.dart
│   │   │           └── profile_screen.dart
│   │   ├── shopping_list/                # Collaborative lists, items & checkoff
│   │   │   ├── data/
│   │   │   │   ├── datasources/
│   │   │   │   │   └── shopping_remote_datasource.dart # Firestore snapshot listener & offline cache
│   │   │   │   ├── models/
│   │   │   │   │   ├── shopping_list_model.dart
│   │   │   │   │   └── shopping_item_model.dart
│   │   │   │   └── repositories/
│   │   │   │       └── shopping_list_repository_impl.dart
│   │   │   ├── domain/
│   │   │   │   ├── entities/
│   │   │   │   │   ├── shopping_list.dart
│   │   │   │   │   └── shopping_item.dart
│   │   │   │   ├── repositories/
│   │   │   │   │   └── shopping_list_repository.dart
│   │   │   │   └── usecases/
│   │   │   │       ├── watch_shopping_items.dart
│   │   │   │       ├── toggle_item_got_it.dart
│   │   │   │       ├── add_shopping_item.dart
│   │   │   │       └── reorder_by_store_route.dart
│   │   │   └── presentation/
│   │   │       ├── bloc/
│   │   │       │   ├── shopping_list_bloc.dart
│   │   │       │   ├── shopping_list_event.dart
│   │   │       │   └── shopping_list_state.dart
│   │   │       ├── screens/
│   │   │       │   ├── shopping_list_screen.dart # Real-time interactive screen
│   │   │       │   └── item_detail_screen.dart
│   │   │       └── widgets/
│   │   │           ├── shopping_item_tile.dart   # Got-it checkoff, price & quantity badge
│   │   │           ├── pending_sync_indicator.dart # Banner/chip indicating queued local writes
│   │   │           ├── store_section_header.dart # Route section groupings
│   │   │           └── quick_add_bar.dart        # Rapid item input with category auto-suggestion
│   │   ├── collaboration/                # Dynamic Links, member management & invitations
│   │   │   ├── data/
│   │   │   │   ├── datasources/
│   │   │   │   │   └── invitation_remote_datasource.dart
│   │   │   │   └── repositories/
│   │   │   │       └── invitation_repository_impl.dart
│   │   │   ├── domain/
│   │   │   │   ├── entities/
│   │   │   │   │   └── list_invitation.dart
│   │   │   │   └── repositories/
│   │   │   │       └── invitation_repository.dart
│   │   │   └── presentation/
│   │   │       ├── bloc/
│   │   │       │   ├── invitation_bloc.dart
│   │   │       │   └── invitation_state.dart
│   │   │       └── screens/
│   │   │           └── share_list_modal.dart
│   │   └── templates/                    # Weekly staples & favorite item templates
│   │       ├── data/
│   │       │   └── models/
│   │       │       └── template_model.dart
│   │       ├── domain/
│   │       │   └── entities/
│   │       │       └── staple_template.dart
│   │       └── presentation/
│   │           └── screens/
│   │               └── staples_manager_screen.dart
│   └── services/                         # External platform integrations & SDK wrappers
│       ├── dynamic_link_service.dart     # Deep link listener (App Links / Universal Links)
│       └── notification_service.dart     # Push notifications for list edits
├── test/                                 # Automated tests suite
│   ├── unit/
│   │   ├── shopping_item_model_test.dart
│   │   └── shopping_list_repository_test.dart
│   ├── bloc/
│   │   └── shopping_list_bloc_test.dart  # bloc_test verifying event -> state streams
│   └── widget/
│       └── shopping_item_tile_test.dart
├── .env.example                          # Environment variable configuration template
├── analysis_options.yaml                 # Static analysis & linter rules
├── codemagic.yaml                        # Continuous Integration & Delivery automation
├── firebase.json                         # Firebase project bindings & emulator configuration
└── pubspec.yaml                          # Dart & Flutter dependencies
```

---

## 1.2 Implementation TODO List

### Phase 1: Foundation (High Priority)
- [x] **Setup & Workspace Infrastructure**: Initialize project folder structure, configuration files, and linting rules.
- [x] **Dependency Setup**: Integrate Flutter BLoC, Firebase Core, Firebase Auth, Cloud Firestore, Equatable, and testing dependencies in `pubspec.yaml`.
- [x] **Firebase Integration & Offline Persistence**:
  - Configure multi-environment client configuration (`.env.example`).
  - Configure Firestore offline cache and write queue tracking.
  - Implement `hasPendingSync` extraction to drive reactive offline indicators.
- [x] **Core Security Rules**: Author production `firestore.rules` verifying authentication, role-based access, and membership in `memberUids`.
- [x] **Authentication Layer (AuthBloc)**:
  - Implement Anonymous Auth for instant onboarding with zero friction.
  - Support Email account linking and upgrade to retain shared lists across devices.
- [x] **Base Domain & Data Models**: Define immutable `ShoppingList` and `ShoppingItem` entities with Firestore converters.

### Phase 2: Core Features (Medium Priority)
- [x] **Real-time Collaborative List (ShoppingListBloc)**:
  - Implement real-time snapshot subscription stream yielding automatic item updates.
  - Optimistic UI updates for the "Got It" checkoff toggle to ensure sub-millisecond local interaction latency.
  - Pending sync indicator tracking items waiting for Firestore server commit.
- [x] **Categorization & Store Sections**:
  - Implement predefined grocery categories (Produce, Dairy, Bakery, Meat, Pantry, Household) with icons and color accents.
  - Add store route ordering algorithm: sort items by physical aisle sequence (e.g., Produce -> Bakery -> Meat -> Dairy -> Frozen).
- [x] **Price Estimator & Quantity Tracking**:
  - Dynamic quantity multiplier (`2x`, `500g`) with unit selection.
  - Real-time running total calculating estimated list cost vs. purchased cart cost.
- [x] **Deep Link / Dynamic Link Invitations**:
  - Build `DynamicLinkService` to handle universal link parsing (`https://shoppinglist.page.link/join?token=XYZ`).
  - Implement Cloud Function token verification and atomic membership addition (`memberUids` array union).
  - Build share sheet UI generating share links, clipboard copy, and member list management.

### Phase 3: Polish & Optimization (Lower Priority)
- [x] **List Templates & Weekly Staples**:
  - Quick-re-add "Favorites" and staples tray on bottom sheet (`StaplesBottomSheet`).
  - Template manager allowing one-click instantiation of "Weekly Groceries" or "BBQ Weekend".
- [x] **Haptic Feedback & Micro-Interactions**:
  - Implement animated container state transitions for "Got It" checkoff.
  - Implement swipe-to-delete with dismissible background.
- [x] **Query Performance & Cache Optimization**:
  - Implement active item filtering to keep real-time listener payload small.
  - Add composite Firestore indexes in `firestore.indexes.json` for fast sorting by `storeSection` and `updatedAt`.
- [x] **CI/CD Pipeline Automation**:
  - Configure Codemagic webhook on branch push for automated static analysis, test runs, and artifact builds (`codemagic.yaml`).
  - Automate build distribution to Firebase App Distribution tester groups (`internal-qa`, `household-beta`).
- [x] **Local Containerized Backend**:
  - Create `Dockerfile` and `docker-compose.yml` to orchestrate Firebase Emulators (Firestore, Auth, Functions, UI) with a single command.
