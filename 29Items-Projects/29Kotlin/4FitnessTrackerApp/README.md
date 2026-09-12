# 4-Fitness-Tracker-App (Android Standalone + Direct Cloud MySQL)

A modern, standalone **Android Application (Kotlin & Jetpack Compose)** that connects **directly** to your **Cloud MySQL Database**, featuring offline-first **Room SQLite caching**, on-device **AI Workout Recommendations**, **Health Connect** synchronization, **Jetpack WorkManager** background sync, and a **1-Tap Magic Workout Logger**.

> [!TIP]
> **Zero Middle Backend Required:** The Android app connects straight to your Cloud MySQL database using Oracle JDBC (`mysql-connector-java:5.1.49`) over TCP. No Ktor/Spring server or Docker container is needed!

---

## 🌟 Complete List of Application Capabilities (34 Features)

1. **User Nickname Prompt on First Launch**
   * Automatically opens an onboarding dialog when the app starts, prompting the user to enter a nickname to create and activate their personal profile.

2. **Multi-User Profile Switcher (5–10 Persons)**
   * Features an interactive profile chip in the top navigation bar, allowing 5–10 different people to quickly switch between profiles on the same device without re-logging in.

3. **User-Isolated Data Storage**
   * Automatically partitions all local Room database entries, Cloud MySQL records, metrics, and goals by the active user's ID to keep each person's progress private and independent.

4. **1-Tap Magic Routine Button**
   * A dedicated quick-action card that instantly records your custom routine (`15 Push ups + 40sec Plank + 15 StepUps`) in one tap without filling out manual forms.

5. **Location Context Selection**
   * Provides 1-tap location chips (`🏢 Office`, `🏡 Home Morning`, `🌳 Garden`, `🏋️ Gym`) directly on the quick routine card to tag where your exercise took place.

6. **Daily Set Counter**
   * Automatically calculates and displays how many times you completed the magic routine today (e.g., *"Today: 3 sets"*), helping you track your daily frequency.

7. **Multi-Sport Manual Workout Logging**
   * A full logging screen supporting a broad range of activities: Running, Cycling, Swimming, Walking, Strength Training, Yoga, and HIIT.

8. **Detailed Metric Inputs**
   * Allows logging duration in minutes, distance in meters/kilometers, intensity level (`LOW`, `MODERATE`, `HIGH`), and optional custom notes for each session.

9. **Smart Calorie Burn Estimation**
   * Uses built-in MET (Metabolic Equivalent of Task) formulas to calculate accurate calorie burn automatically if you leave the calories field blank.

10. **Interactive Circular Progress Ring**
    * A central dashboard visualizer showing an animated neon progress arc of calories burned today compared against your target goal (e.g., `480 / 600 kcal`).

11. **Weekly Workouts Metric Card**
    * A quick-glance dashboard card that tallies and displays the total number of workout sessions logged during the current calendar week.

12. **Daily Active Time Metric Card**
    * A dashboard summary card that aggregates all active exercise minutes accumulated throughout the day.

13. **Daily Calorie Burn Metric Card**
    * A summary card that displays the combined total of all active calories burned from both manual workouts and magic routine sets today.

14. **Recent Activity Feed**
    * A scrollable chronological feed on the dashboard displaying recently logged workouts with activity icons, duration badges, and location tags.

15. **Custom Goal Creation**
    * Allows setting personalized targets for daily calories, weekly workout counts, running distance, or active minutes with customized deadlines.

16. **Live Goal Progress Bars**
    * Visual linear progress indicators on the Goals screen that update in real time as new workouts are logged.

17. **Automatic Goal Completion Tracking**
    * Automatically calculates when a goal target is reached, updates the database status, and displays a completion badge.

18. **Fatigue & Overload Detection (AI Coach)**
    * Analyzes your workout frequency and high-intensity volume over the past 3–7 days to detect fatigue and protect against overtraining.

19. **Active Recovery Recommendations (AI Coach)**
    * Intelligently prescribes light recovery sessions (such as 20 min of Yoga or light Walking) after demanding workout streaks.

20. **Cross-Training Progression (AI Coach)**
    * Evaluates your training patterns and suggests alternative sports (e.g., Swimming or Cycling after heavy running) to balance muscle groups and prevent plateaus.

21. **1-Tap AI Suggestion Acceptance**
    * Allows you to tap *"Accept"* on any AI Coach recommendation, which immediately pre-fills the logging form with the suggested sport, duration, and intensity.

22. **100% On-Device ML Processing**
    * Executes all predictive recommendation algorithms entirely within Kotlin on your phone, ensuring instant response times with zero external API fees.

23. **Offline-First Local Database (Room SQLite)**
    * Keeps a full local cache of all data using Android Room, ensuring the app is fast and completely usable even with no internet connection or in airplane mode.

24. **Direct Cloud MySQL Connection**
    * Connects directly from the Android app to Cloud MySQL (`z3t77r.h.filess.io:3306`) using Oracle's official JDBC driver (`mysql-connector-java:5.1.49`).

25. **Automatic Cloud Database Schema Provisioning**
    * Automatically executes DDL scripts on the remote database upon first connection to ensure `users`, `workouts`, and `goals` tables and indexes exist.

26. **Instant Auto-Sync on Save**
    * Spawns a background coroutine every time a workout or magic routine is logged to push the new record to Cloud MySQL immediately.

27. **Periodic Background Auto-Sync (WorkManager)**
    * Uses Android's `WorkManager` to run an automatic synchronization job every 15 minutes and immediately whenever internet connectivity is restored.

28. **In-App Database Configuration Screen**
    * A dedicated settings screen (via the ⚙️ top bar icon) to input and edit MySQL Host, Port, Database Name, Username, and Password.

29. **Live Database "Test Ping" Button**
    * Runs a live `SELECT 1` query over a direct socket to test your credentials and host reachability before saving.

30. **Live Diagnostic Error Reporting**
    * Intercepts connection failures (e.g. wrong password, unreachable host, closed port) and displays a clear explanation card on-screen without crashing.

31. **Manual "Sync Now" Button**
    * An on-demand button in settings that forces an immediate batch synchronization of all pending local records with the cloud database.

32. **Password Visibility Toggle**
    * An eye icon toggle in the database settings form to securely view or mask the MySQL password while typing.

33. **Adaptive Dark / Light Material 3 Theme**
    * Built with Google's modern Material Design 3 guidelines, featuring customized typography, rounded corners, dynamic surfaces, and dark mode support.

34. **Custom Adaptive Launcher Icons**
    * Features tailored foreground and background vector layers that adapt cleanly to square, circular, and teardrop Android home screen launchers.

---

## 🏗 Standalone Architecture (No Backend Server Needed)

```
┌───────────────────────────────────────────────────────────────────────────────────┐
│                        ANDROID CLIENT (KOTLIN & COMPOSE)                          │
│  - Jetpack Compose UI (Material 3)                                                │
│  - ViewModels & Unidirectional Data Flow (StateFlow)                              │
│  - Domain UseCases (LogWorkout, GetGoals, GetRecommendations)                     │
│  - Local Room SQLite DB (Encrypted Cache & SSOT)                                  │
│  - Health Connect Client (Step Counts, Calories)                                  │
│  - WorkManager (Periodic & Expedited Background Sync)                             │
│  - DirectMySqlManager (Oracle JDBC 5.1.49 Client with Auto-DDL)                   │
│  - DatabaseConfigManager & UserSessionManager                                     │
└─────────────────────────────────────────┬─────────────────────────────────────────┘
                                          │
                                          │ (Direct JDBC TCP Connection over Port 3306)
                                          ▼
┌───────────────────────────────────────────────────────────────────────────────────┐
│                    CLOUD DATABASE (MYSQL 8.0 / FILess.io)                         │
│  - Tables: users, workouts, goals                                                 │
│  - Auto-created by DirectMySqlManager upon first connection                       │
│  - Composite indexes on (user_id, start_time)                                     │
└───────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🚀 Quick Start: Running the Android App

### 1. Build from CLI
```powershell
# Build Debug APK
.\gradlew.bat assembleDebug

# Build Signed Standalone Release APK
.\gradlew.bat assembleRelease
```

### 2. Output APK Locations
- **Release APK:** `app\build\outputs\apk\release\app-release.apk`
- **Debug APK:** `app\build\outputs\apk\debug\app-debug.apk`

### 3. Pre-configured Cloud MySQL Defaults
- **Host:** `z3t77r.h.filess.io`
- **Port:** `3306`
- **Database:** `fitness_app_db_joineddead`
- **User:** `fitness_app_db_joineddead`

---

## 🧪 Running Automated Android Tests

```powershell
.\gradlew.bat testDebugUnitTest
```

---

## 📄 Documentation Links
- [PROJECT-PLAN.md](file:///c:/mygit/JavaRelated/29Items-Projects/29Kotlin/4FitnessTrackerApp/docs/PROJECT-PLAN.md) — Comprehensive plan and roadmap.
- [ARCHITECTURE.md](file:///c:/mygit/JavaRelated/29Items-Projects/29Kotlin/4FitnessTrackerApp/docs/ARCHITECTURE.md) — Architectural diagrams, data flows, and security guidelines.
- [TECH-NOTES.md](file:///c:/mygit/JavaRelated/29Items-Projects/29Kotlin/4FitnessTrackerApp/docs/TECH-NOTES.md) — CI/CD, testing matrix, and deployment guide.
