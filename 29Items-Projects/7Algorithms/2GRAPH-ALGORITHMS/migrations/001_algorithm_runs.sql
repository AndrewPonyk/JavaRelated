CREATE TABLE IF NOT EXISTS algorithm_runs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    algorithm_name TEXT NOT NULL CHECK(length(trim(algorithm_name)) > 0),
    language TEXT NOT NULL CHECK(language IN ('python', 'java')),
    input_summary TEXT NOT NULL,
    output_summary TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_algorithm_runs_created_at
ON algorithm_runs (created_at);
