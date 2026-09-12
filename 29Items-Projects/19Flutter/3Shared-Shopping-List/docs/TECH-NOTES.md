# Technical Notes: Shared Shopping List

Practical, battle-tested engineering guidelines covering CI/CD pipelines, automated testing, cloud deployment, environment isolation, Git workflows, and common technical pitfalls for the **Shared Shopping List** Flutter & Firebase stack.

---

## 3.1 CI/CD Pipeline Design

The Continuous Integration & Delivery workflow is orchestrated via **Codemagic** as the primary mobile pipeline, complemented by **GitHub Actions** for fast branch linting and unit testing.

```
+-------------------------------------------------------------------------------+
|                       CODEMAGIC CI/CD PIPELINE PHASES                         |
+-------------------------------------------------------------------------------+
  [Trigger: PR / Tag]
          |
          v
  Phase 1: Environment & Dependencies
  - Select Flutter SDK 3.x channel
  - Restore CocoaPods & Gradle caches
  - Run `flutter pub get`
          |
          v
  Phase 2: Code Quality & Static Analysis
  - `dart format --set-exit-if-changed .`
  - `flutter analyze --fatal-infos`
          |
          v
  Phase 3: Automated Test Suite
  - Run unit & BLoC tests: `flutter test --coverage`
  - Check coverage threshold (>= 80%)
  - Publish JUnit report & codecov upload
          |
          v
  Phase 4: Artifact Compilation
  - Android: `flutter build appbundle --flavor prod --dart-define-from-file=.env`
  - iOS: `flutter build ipa --flavor prod --export-options-plist=ExportOptions.plist`
          |
          v
  Phase 5: Distribution & Deployment
  - Dev/Staging: Upload APK & IPA to Firebase App Distribution with release notes
  - Production: Push AAB to Google Play Internal Track & IPA to TestFlight
```

### Codemagic Pipeline Configuration Highlights
- **Build Caching**: Cache `~/.pub-cache`, `android/.gradle`, and `ios/Pods` to reduce cold build times from ~18 minutes to ~5 minutes.
- **Artifact Signing**: Android keystore and Apple App Store Distribution certificates stored securely in Codemagic encrypted environment variables.
- **Firebase App Distribution Integration**: Automatic dispatch to Firebase tester groups (`internal-dev`, `beta-households`) via Firebase CLI token or service account key.

---

## 3.2 Testing Strategy

The testing pyramid ensures high velocity without sacrificing correctness in real-time collaborative flows.

```
       /\
      /  \       E2E / Integration Tests (10%)
     /----\      - integration_test: full shopping trip & invite redemption
    /      \
   /--------\    Widget Tests (25%)
  /          \   - Screen rendering, Got It animation, pending sync indicator
 /------------\
/              \ Unit & BLoC Tests (65%)
---------------- - Domain entities, BLoC state streams, model serialization
```

### 1. Unit & BLoC Testing
- **Frameworks**: `flutter_test`, `bloc_test`, `mocktail`.
- **Target Coverage**: $\ge 85\%$ for Domain and BLoC layers; $\ge 90\%$ for JSON/Firestore serialization.
- **Testing BLoC State Streams**:
  ```dart
  blocTest<ShoppingListBloc, ShoppingListState>(
    'emits [loading, loaded] when items stream emits updates',
    build: () {
      when(() => mockRepo.watchItems(any()))
          .thenAnswer((_) => Stream.value([tItem1, tItem2]));
      return ShoppingListBloc(shoppingListRepository: mockRepo);
    },
    act: (bloc) => bloc.add(const LoadShoppingList('list-001')),
    expect: () => [
      const ShoppingListState(status: ShoppingListStatus.loading),
      ShoppingListState(status: ShoppingListStatus.loaded, items: [tItem1, tItem2]),
    ],
  );
  ```

### 2. Widget Testing
- Test user interactions in isolation with mocked dependencies.
- Verify that tapping the "Got It" checkbox immediately updates visual checkbox state and dispatches the corresponding event without awaiting network responses.

### 3. Integration & Offline Sync Testing
- Use Flutter's native `integration_test` harness.
- Simulate airplane mode or disconnect WiFi to verify that:
  - Items can still be added to the shopping list.
  - The `PendingSyncIndicator` displays "1 pending update".
  - Re-enabling connectivity clears the pending indicator and replicates the item to Firestore.

---

## 3.3 Deployment Strategy

### Mobile App Deployment (Flutter)
1. **Internal Testing**:
   - Every commit merged to `main` triggers a Codemagic build distributed via **Firebase App Distribution**.
   - Testers receive instant in-app alerts via the Firebase App Distribution SDK.
2. **Beta Testing**:
   - Tagged releases (e.g. `v1.2.0-beta.1`) trigger automated builds sent to **Google Play Open Testing** and **Apple TestFlight**.
3. **Production Deployment**:
   - Release candidate branches (`release/v1.x`) trigger production builds with staged rollout (10% -> 25% -> 50% -> 100%).

### Backend & Cloud Functions Deployment
- Cloud Functions are deployed via the Firebase CLI using CI runner service accounts:
  ```bash
  firebase deploy --only firestore:rules,firestore:indexes,functions
  ```
- Functions use Node.js 20 LTS runtime with strict TypeScript compilation.
- Rules and indexes are version-controlled alongside application code in `/firebase/`.

---

## 3.4 Environment Management

Three distinct environments guarantee that test mutations do not corrupt real household shopping lists:

| Environment | Firebase Project ID | Purpose | Auth Domains |
| :--- | :--- | :--- | :--- |
| **Development** (`dev`) | `shopping-list-dev` | Local development, emulator suite | `localhost`, `dev.shopping.page.link` |
| **Staging / QA** (`staging`) | `shopping-list-staging` | Internal household testing | `staging.shopping.page.link` |
| **Production** (`prod`) | `shopping-list-prod` | App Store & Google Play live users | `shopping.page.link`, `app.shoppinglist.com` |

### Configuration Injection
Use compile-time environment variables via `--dart-define-from-file=.env`:
```bash
flutter run --flavor dev --dart-define-from-file=.env.dev
flutter build appbundle --flavor prod --dart-define-from-file=.env.prod
```

### Template: `.env.example`
The repository includes `.env.example` defining all required configuration keys.

---

## 3.5 Version Control Workflow

### Workflow: Trunk-Based Development with Short-Lived Feature Branches

```
main (always releasable, deployed to Dev/QA)
  │
  ├── feat/optimistic-got-it-checkoff ────► PR (CI checks pass) ──► Squash & Merge
  │
  ├── fix/firestore-listener-leak ────────► PR (CI checks pass) ──► Squash & Merge
  │
  └── tag: v1.1.0 ───────────────────────────────────────────────► Trigger Release Build
```

### Branching Rules
1. **Branch Naming**:
   - `feat/feature-name` (e.g., `feat/store-section-routing`)
   - `fix/bug-description` (e.g., `fix/pending-sync-indicator-counter`)
   - `chore/tool-or-dep` (e.g., `chore/upgrade-flutter-3-x`)
2. **Pull Requests**:
   - Mandatory passing CI (analyze, format, test).
   - At least 1 peer review approval.
   - PRs must be squash-merged to preserve a linear, bisectable commit log on `main`.
3. **Semantic Versioning & Git Tags**:
   - Tags follow `vMAJOR.MINOR.PATCH+BUILD` (e.g., `v1.2.0+42`).
   - Pushing a tag automatically triggers Codemagic release pipelines.

---

## 3.6 Common Pitfalls & Solutions

### 1. Unbounded Firestore Snapshot Listeners (Memory & Billing Leaks)
- **Problem**: Subscribing to `snapshots()` inside Flutter widgets or BLoCs without canceling subscriptions leaks memory and causes explosive read charges.
- **Solution**: Always manage `StreamSubscription` lifecycles. Cancel them in `BLoC.close()` or convert them to BLoC event streams using `emit.forEach(...)` with built-in subscription cancellation.

### 2. Race Conditions in Collaborative Checkoffs
- **Problem**: Shopper A checks off an item while Shopper B edits its quantity. An uncoordinated full document overwrite by Shopper B resets Shopper A's checkoff.
- **Solution**: Use granular field updates:
  ```dart
  // Correct: Only mutates the specific boolean field
  await docRef.update({'isGotIt': true, 'updatedAt': FieldValue.serverTimestamp()});
  ```
  Never call `.set(completeObject)` for partial status changes.

### 3. Firebase Dynamic Links Deprecation Path
- **Problem**: Google deprecated Firebase Dynamic Links (scheduled sunset).
- **Solution**: Structure deep linking architecture using modern OS-level **Android App Links** (via `.well-known/assetlinks.json`) and **iOS Universal Links** (via `.well-known/apple-app-site-association`), coordinated through the cross-platform `app_links` package with fallback server routing.

### 4. Offline Writes & Server Timestamps
- **Problem**: Calling `FieldValue.serverTimestamp()` when offline stores a local `null` until the write reaches the server, causing NPEs or unexpected null values in timestamp fields.
- **Solution**: In model deserialization, handle pending timestamps gracefully:
  ```dart
  updatedAt: (data['updatedAt'] as Timestamp?)?.toDate() ?? DateTime.now(),
  ```

### 5. Document Size vs. Subcollection Scalability
- **Problem**: Storing all list items inside an array within the `ShoppingList` document hits Firestore's 1MB document size limit and 1 write/sec throttle.
- **Solution**: Always model items as a subcollection: `/shopping_lists/{listId}/items/{itemId}`.
