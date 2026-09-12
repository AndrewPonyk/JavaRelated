CREATE TABLE IF NOT EXISTS compile_jobs (
    id UUID PRIMARY KEY,
    source TEXT NOT NULL,
    options JSONB NOT NULL DEFAULT '{}'::jsonb,
    status TEXT NOT NULL CHECK (status IN ('pending', 'succeeded', 'failed')),
    emitted_javascript TEXT,
    source_map JSONB,
    diagnostics JSONB NOT NULL DEFAULT '[]'::jsonb,
    cache_key TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_compile_jobs_created_at ON compile_jobs (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_compile_jobs_cache_key ON compile_jobs (cache_key);

CREATE TABLE IF NOT EXISTS compile_artifacts (
    id UUID PRIMARY KEY,
    compile_job_id UUID NOT NULL REFERENCES compile_jobs (id) ON DELETE CASCADE,
    artifact_type TEXT NOT NULL CHECK (artifact_type IN ('javascript', 'source_map', 'diagnostics')),
    content JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_compile_artifacts_job_id ON compile_artifacts (compile_job_id);
