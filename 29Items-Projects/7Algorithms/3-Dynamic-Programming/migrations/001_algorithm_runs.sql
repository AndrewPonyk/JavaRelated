CREATE TABLE IF NOT EXISTS algorithm_runs (
    id INTEGER PRIMARY KEY,
    algorithm_name VARCHAR(100) NOT NULL,
    language VARCHAR(20) NOT NULL,
    variant VARCHAR(50) NOT NULL,
    input_summary TEXT NOT NULL,
    result_summary TEXT NOT NULL,
    duration_ms INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_algorithm_runs_name
    ON algorithm_runs (algorithm_name, language, variant);
