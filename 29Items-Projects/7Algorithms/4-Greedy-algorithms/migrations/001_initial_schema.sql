CREATE TABLE IF NOT EXISTS algorithm_scenarios (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    algorithm TEXT NOT NULL,
    input_json TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS algorithm_runs (
    id INTEGER PRIMARY KEY,
    scenario_id INTEGER NOT NULL,
    language TEXT NOT NULL CHECK (language IN ('java', 'python')),
    output_json TEXT NOT NULL,
    duration_ms INTEGER,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (scenario_id) REFERENCES algorithm_scenarios(id)
);
