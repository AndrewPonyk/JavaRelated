-- ====================================================================
-- Fitness Tracker Database Schema (Cloud MySQL 8.0)
-- ====================================================================

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) UNIQUE,
    username VARCHAR(100) NOT NULL,
    created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Workouts Table
CREATE TABLE IF NOT EXISTS workouts (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    workout_type VARCHAR(50) NOT NULL,
    duration_minutes INT NOT NULL,
    calories_burned INT NOT NULL,
    distance_meters DOUBLE DEFAULT 0.0,
    intensity_level VARCHAR(20) DEFAULT 'MODERATE',
    notes TEXT,
    start_time BIGINT NOT NULL,
    sync_status VARCHAR(20) DEFAULT 'SYNCED',
    INDEX idx_user_start_time (user_id, start_time),
    INDEX idx_user_sync_status (user_id, sync_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Goals Table
CREATE TABLE IF NOT EXISTS goals (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    goal_type VARCHAR(50) NOT NULL,
    target_value DOUBLE NOT NULL,
    current_value DOUBLE DEFAULT 0.0,
    start_date VARCHAR(20) NOT NULL,
    end_date VARCHAR(20),
    is_completed BOOLEAN DEFAULT FALSE,
    INDEX idx_user_goal (user_id, is_completed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Recommendations Table
CREATE TABLE IF NOT EXISTS recommendations (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    suggested_type VARCHAR(50) NOT NULL,
    suggested_duration_minutes INT NOT NULL,
    suggested_intensity VARCHAR(20) NOT NULL,
    rationale TEXT NOT NULL,
    confidence_score DOUBLE NOT NULL,
    created_at BIGINT NOT NULL,
    INDEX idx_user_rec (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
