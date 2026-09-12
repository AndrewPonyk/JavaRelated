# Project Plan: Fitness Tracker App (Standalone Android + Cloud MySQL)

## 1. Executive Summary & Context
- **Project Name:** Fitness Tracker App
- **Primary Tech Stack:** 
  - **Mobile Frontend:** Kotlin, Jetpack Compose (Material 3), Jetpack Navigation Compose, Coroutines/Flow
  - **Local Storage & Health Data:** Room SQLite DB, Android Health Connect API, Android Jetpack WorkManager
  - **Cloud Synchronization:** Direct JDBC TCP connection to Cloud MySQL (No middle server required!)
  - **Machine Learning:** On-device adaptive ML workout recommendation engine with fatigue and recovery detection
  - **CI/CD & Distribution:** GitHub Actions, Google Play Console (Internal/Production tracks)

---

## 1.1 Project File Structure

```
4FitnessTrackerApp/
├── .github/
│   └── workflows/
│       ├── android-ci.yml                 # Android linting, unit tests, and build verification
│       └── release-playstore.yml          # Automated deployment to Google Play Internal Test
├── config/
│   ├── detekt/
│   │   └── detekt.yml                     # Kotlin static analysis rules
│   └── checkstyle/
│       └── checkstyle.xml                 # Code style verification
├── docs/
│   ├── PROJECT-PLAN.md                    # Project roadmap and breakdown (this file)
│   ├── ARCHITECTURE.md                    # System architecture, data flow, security, diagrams
│   └── TECH-NOTES.md                      # Engineering guidelines, CI/CD, testing, environment specs
├── app/                                   # Android Application Module
│   ├── build.gradle.kts                   # App-level build configuration (Compose, Room, Health Connect, MySQL JDBC)
│   ├── proguard-rules.pro                 # Proguard / R8 code shrinking rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml        # App manifest + Health Connect permission declarations
│       │   ├── assets/schema/
│       │   │   └── V1__init_schema.sql    # Cloud MySQL DDL schema
│       │   ├── java/com/example/a4_fitness_tracker_app/
│       │   │   ├── FitnessTrackerApp.kt   # Application class (DI / WorkManager / DirectMySql init)
│       │   │   ├── MainActivity.kt        # Main single-activity host with Navigation & Profile Switcher
│       │   │   ├── data/                  # Data Layer (Repositories, Local DB, Direct MySQL, Health Connect)
│       │   │   │   ├── local/
│       │   │   │   │   ├── AppDatabase.kt
│       │   │   │   │   ├── UserSessionManager.kt       # Multi-user profile management (5-10 persons)
│       │   │   │   │   ├── DatabaseConfigManager.kt    # In-app MySQL credentials persistence
│       │   │   │   │   ├── dao/
│       │   │   │   │   │   ├── WorkoutDao.kt
│       │   │   │   │   │   └── GoalDao.kt
│       │   │   │   │   └── entity/
│       │   │   │   │       ├── WorkoutEntity.kt
│       │   │   │   │       └── GoalEntity.kt
│       │   │   │   ├── healthconnect/
│       │   │   │   │   └── HealthConnectManager.kt     # Health Connect SDK wrapper
│       │   │   │   ├── remote/
│       │   │   │   │   └── DirectMySqlManager.kt       # Direct JDBC connection to Cloud MySQL
│       │   │   │   └── repository/
│       │   │   │       ├── WorkoutRepositoryImpl.kt
│       │   │   │       ├── GoalRepositoryImpl.kt
│       │   │   │       └── RecommendationRepositoryImpl.kt
│       │   │   ├── domain/                # Domain Layer (Models, UseCases, Repository Interfaces)
│       │   │   │   ├── model/
│       │   │   │   │   ├── Workout.kt
│       │   │   │   │   ├── Goal.kt
│       │   │   │   │   └── Recommendation.kt
│       │   │   │   ├── repository/
│       │   │   │   │   ├── WorkoutRepository.kt
│       │   │   │   │   ├── GoalRepository.kt
│       │   │   │   │   └── RecommendationRepository.kt
│       │   │   │   └── usecase/
│       │   │   │       ├── LogWorkoutUseCase.kt
│       │   │   │       ├── GetGoalsUseCase.kt
│       │   │   │       ├── UpdateGoalProgressUseCase.kt
│       │   │   │       ├── GetWorkoutStatsUseCase.kt
│       │   │   │       └── GetRecommendationsUseCase.kt
│       │   │   ├── ml/                    # On-device Machine Learning / Heuristics
│       │   │   │   └── LocalWorkoutPredictor.kt
│       │   │   ├── workers/               # Jetpack WorkManager background sync workers
│       │   │   │   ├── HealthDataSyncWorker.kt
│       │   │   │   └── PeriodicSyncWorkerScheduler.kt
│       │   │   └── ui/                    # Presentation Layer (Jetpack Compose + Material 3)
│       │   │       ├── theme/
│       │   │       │   ├── Color.kt
│       │   │       │   ├── Theme.kt
│       │   │       │   └── Type.kt
│       │   │       ├── components/        # Reusable UI widgets
│       │   │       │   ├── WorkoutCard.kt
│       │   │       │   ├── GoalCard.kt
│       │   │       │   ├── ProgressRing.kt
│       │   │       │   ├── SpecialRoutineMagicButton.kt # 1-Tap 15 Push ups + 40sec Plank + 15 StepUps
│       │   │       │   ├── UserSelectionDialog.kt       # Multi-user Nickname picker
│       │   │       │   └── StateViews.kt
│       │   │       └── screens/
│       │   │           ├── workout/
│       │   │           │   ├── WorkoutLoggingScreen.kt
│       │   │           │   ├── WorkoutViewModel.kt
│       │   │           │   └── WorkoutUiState.kt
│       │   │           ├── dashboard/
│       │   │           │   ├── DashboardScreen.kt
│       │   │           │   └── DashboardViewModel.kt
│       │   │           ├── goals/
│       │   │           │   ├── GoalsScreen.kt
│       │   │           │   └── GoalsViewModel.kt
│       │   │           ├── recommendations/
│       │   │           │   ├── RecommendationScreen.kt
│       │   │           │   └── RecommendationViewModel.kt
│       │   │           └── settings/
│       │   │               ├── ServerSettingsScreen.kt  # Direct MySQL Credentials & Test Ping
│       │   │               └── ServerSettingsViewModel.kt
│       │   └── res/                       # Drawables, strings, mipmaps, colors
│       └── test/                          # Unit Tests (JUnit, Coroutines Test)
├── .editorconfig                          # Code style settings
├── .env.example                           # Environment configuration template
├── build.gradle.kts                       # Root project build configuration
├── gradle.properties                      # Gradle JVM & build flags
├── gradlew.bat                            # Windows Gradle wrapper script
└── settings.gradle.kts                    # Gradle multi-project inclusions
```

---

## 1.2 Implementation Checklist

### Phase 1: Foundation
- [x] Project layout and configuration for Jetpack Compose (Material 3), Room, WorkManager, and Health Connect.
- [x] Local Room Database schema: `WorkoutEntity`, `GoalEntity`, `WorkoutDao`, `GoalDao`, `AppDatabase`.
- [x] Cloud MySQL DDL schema (`V1__init_schema.sql`).
- [x] Core Domain models and clean repository contracts.
- [x] Material 3 dynamic color scheme, typography, and dark theme.

### Phase 2: Core Features
- [x] UI Components: `WorkoutCard`, `ProgressRing`, `GoalCard`, `SpecialRoutineMagicButton`, `UserSelectionDialog`.
- [x] **1-Tap Magic Button:** Logs `15 Push ups + 40sec Plank + 15 StepUps` with attempt tracking, location tagging, and timestamping.
- [x] **Multi-User Profiles:** Onboarding and profile switching supporting 5–10 users with Nickname isolation.
- [x] **Direct Cloud MySQL Sync:** `DirectMySqlManager` syncing Room SQLite directly to Cloud MySQL without a middle backend server.
- [x] **In-App Database Settings:** Configure MySQL host, port, credentials, test ping, and instant sync.
- [x] **On-Device AI Workout Coach:** `LocalWorkoutPredictor` analyzing workout patterns, fatigue levels, and cross-training progression.
- [x] **Health Connect Integration:** Reading steps and calories directly into dashboard progress rings.

### Phase 3: Production Polish & Standalone Architecture
- [x] Removed external backend dependency; all ML, sync, and business logic runs inside the Android application.
- [x] Full offline-first caching policy with Room SQLite as Single Source of Truth.
- [x] Complete unit test suite for UseCases, ViewModels, and Predictors.
