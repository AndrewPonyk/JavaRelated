# System Architecture: Fitness Tracker App (Standalone Android + Cloud MySQL)

## 2.1 Chosen Architectural Pattern

The Fitness Tracker system employs **Clean Architecture with MVVM (Model-View-ViewModel)** on the Android client, communicating **directly with Cloud MySQL via JDBC over SSL/TCP**.

```
+-----------------------------------------------------------------------------------+
|                              PRESENTATION LAYER                                   |
|   Jetpack Compose UI (Material 3)  <--->  StateFlow / ViewModels (Unidirectional) |
+-----------------------------------------+-----------------------------------------+
                                          |
+-----------------------------------------v-----------------------------------------+
|                                DOMAIN LAYER                                       |
|           UseCases / Interactors   <--->   Domain Models & Interfaces             |
+--------------------+------------------------------------+-------------------------+
                     |                                    |
+--------------------v-------------------+   +------------v-------------------------+
|           DATA LAYER (LOCAL)           |   |          DATA LAYER (DIRECT SYNC)    |
| - Room Database (SQLite Engine)        |   | - DirectMySqlManager (JDBC Driver)   |
| - Health Connect Android API           |   | - WorkManager Background Sync Engine |
| - On-Device ML Workout Predictor       |   | - Cloud MySQL Relational DB          |
+----------------------------------------+   +--------------------------------------+
```

### Justification:
1. **Zero Backend Maintenance:** No separate Ktor/Spring server or Docker containers are required. The phone syncs directly to Cloud MySQL.
2. **Offline-First Resilience:** Room SQLite acts as the local Single Source of Truth (SSOT). All logging and viewing works without an internet connection.
3. **On-Device AI Recommendation Engine:** The ML predictor runs 100% locally on device, analyzing fatigue, intensity, and cross-training patterns without cloud latency.
4. **Health Connect Compliant:** Handles Google Health Connect steps, active calories, and exercise records seamlessly.

---

## 2.2 Key Component Interactions

```mermaid
graph TD
    UI[Jetpack Compose Screens] -->|Events / Actions| VM[ViewModels]
    VM -->|Observes StateFlow| UI
    VM -->|Executes| UC[Domain UseCases]
    UC -->|Invokes| REPO[Workout & Sync Repositories]
    
    REPO -->|Read/Write Entity| ROOM[(Local Room SQLite DB)]
    REPO -->|Query Aggregate Data| HC[Health Connect Client]
    REPO -->|Enqueue Periodic Task| WM[Jetpack WorkManager]
    REPO -->|Predict Next Session| ML[On-Device ML Predictor]
    
    WM -->|Direct JDBC TCP Sync| MYSQL[(Cloud MySQL Database)]
```

---

## 2.3 Data Flow & Sequence Diagram

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant ComposeUI as Compose UI
    participant ViewModel as WorkoutViewModel
    participant UseCase as LogWorkoutUseCase
    participant Repo as WorkoutRepository
    participant RoomDB as Room Database (Local)
    participant WorkMgr as WorkManager
    participant DirectMySQL as DirectMySqlManager (JDBC)
    participant CloudMySQL as Cloud MySQL Database
    participant MLPredictor as Local ML Predictor

    User->>ComposeUI: Taps ⚡ Quick Routine or logs workout
    ComposeUI->>ViewModel: submitWorkout()
    ViewModel->>UseCase: execute(WorkoutInput)
    UseCase->>Repo: saveWorkout(Workout)
    Repo->>RoomDB: insertWorkout(WorkoutEntity, syncState='PENDING')
    RoomDB-->>Repo: Saved Success (ID)
    Repo-->>ViewModel: Result.Success(Workout)
    ViewModel-->>ComposeUI: Emit UiState.Success & update rings
    
    Note over Repo,WorkMgr: Trigger Background Cloud Sync
    Repo->>WorkMgr: Enqueue Periodic / Expedited Sync
    WorkMgr->>DirectMySQL: syncWorkouts(pendingList)
    DirectMySQL->>CloudMySQL: INSERT INTO workouts ... ON DUPLICATE KEY UPDATE
    CloudMySQL-->>DirectMySQL: SQL Batch Success
    DirectMySQL-->>WorkMgr: Result.Success(count)
    WorkMgr->>RoomDB: updateSyncStatus(ids, 'SYNCED')
    
    Note over ComposeUI,MLPredictor: On-Device AI Coaching
    ComposeUI->>MLPredictor: predictNextWorkouts(history)
    MLPredictor-->>ComposeUI: Display cross-training & active recovery recommendations
```
