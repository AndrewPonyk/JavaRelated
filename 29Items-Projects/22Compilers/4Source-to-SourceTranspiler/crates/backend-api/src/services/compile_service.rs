use std::sync::Arc;

use chrono::{DateTime, Utc};
use serde_json::Value;
use tokio_postgres::{Client, NoTls, Row};
use transpiler_core::{CompileOptions, CompileOutput, Diagnostic, DiagnosticSeverity};
use transpiler_emitter::{EmitOptions, JavaScriptEmitter};
use transpiler_incremental::CacheKey;
use transpiler_macros::MacroExpansionContext;
use transpiler_transforms::TransformPipeline;
use uuid::Uuid;

use crate::models::compile_job::{
    CompileArtifact, CompileArtifactType, CompileJob, CompileJobStatus, CreateCompileJobRequest,
    UpdateCompileJobRequest,
};

const DEFAULT_MAX_SOURCE_BYTES: usize = 1024 * 1024;
const COMPILER_VERSION: &str = env!("CARGO_PKG_VERSION");

#[derive(Debug, thiserror::Error)]
pub enum ServiceError {
    #[error("source must not be empty")]
    EmptySource,
    #[error("source must be at most {max} bytes")]
    SourceTooLarge { max: usize },
    #[error("target must be one of: es2020, es2021, es2022, esnext")]
    InvalidTarget,
    #[error("compile job not found")]
    NotFound,
    #[error("database operation failed: {0}")]
    Database(#[from] tokio_postgres::Error),
    #[error("stored row contains invalid status '{0}'")]
    InvalidStatus(String),
    #[error("stored row contains invalid artifact type '{0}'")]
    InvalidArtifactType(String),
    #[error("serialization failed: {0}")]
    Serialization(#[from] serde_json::Error),
}

#[derive(Clone)]
pub struct CompileService {
    client: Arc<Client>,
    max_source_bytes: usize,
}

impl CompileService {
    pub async fn connect(database_url: &str) -> Result<Self, ServiceError> {
        let (client, connection) = tokio_postgres::connect(database_url, NoTls).await?;
        tokio::spawn(async move {
            if let Err(error) = connection.await {
                tracing::error!(%error, "database connection task failed");
            }
        });

        let service = Self {
            client: Arc::new(client),
            max_source_bytes: std::env::var("MAX_SOURCE_BYTES")
                .ok()
                .and_then(|value| value.parse::<usize>().ok())
                .unwrap_or(DEFAULT_MAX_SOURCE_BYTES),
        };
        service.run_migrations().await?;
        Ok(service)
    }

    pub async fn run_migrations(&self) -> Result<(), ServiceError> {
        self.client
            .batch_execute(include_str!("../../../../migrations/001_create_compile_jobs.sql"))
            .await?;
        Ok(())
    }

    pub async fn list(&self, limit: i64, offset: i64) -> Result<Vec<CompileJob>, ServiceError> {
        let rows = self
            .client
            .query(
                "SELECT * FROM compile_jobs ORDER BY created_at DESC LIMIT $1 OFFSET $2",
                &[&limit, &offset],
            )
            .await?;
        rows.into_iter().map(row_to_job).collect()
    }

    pub async fn get(&self, id: Uuid) -> Result<Option<CompileJob>, ServiceError> {
        let row = self
            .client
            .query_opt("SELECT * FROM compile_jobs WHERE id = $1", &[&id])
            .await?;
        row.map(row_to_job).transpose()
    }

    pub async fn create(
        &self,
        request: CreateCompileJobRequest,
    ) -> Result<CompileJob, ServiceError> {
        self.validate_source(&request.source)?;
        validate_options(&request.options)?;

        let output = self.compile_or_get_cached(&request.source, &request.options).await?;
        let status = status_from_output(&output);
        let cache_key = cache_key(&request.source, &request.options);

        let job = self
            .insert_job(request.source, request.options, status, output, cache_key)
            .await?;
        Ok(job)
    }

    pub async fn update(
        &self,
        id: Uuid,
        request: UpdateCompileJobRequest,
    ) -> Result<CompileJob, ServiceError> {
        let existing = self.get(id).await?.ok_or(ServiceError::NotFound)?;
        let source = request.source.unwrap_or(existing.source);
        let options = request.options.unwrap_or(existing.options);

        self.validate_source(&source)?;
        validate_options(&options)?;

        let output = self.compile_or_get_cached(&source, &options).await?;
        let status = status_from_output(&output);
        let cache_key = cache_key(&source, &options);
        let job = self
            .update_job(id, source, options, status, output, cache_key)
            .await?;
        Ok(job)
    }

    pub async fn delete(&self, id: Uuid) -> Result<bool, ServiceError> {
        let deleted = self
            .client
            .execute("DELETE FROM compile_jobs WHERE id = $1", &[&id])
            .await?;
        Ok(deleted > 0)
    }

    pub async fn list_artifacts(
        &self,
        compile_job_id: Uuid,
    ) -> Result<Vec<CompileArtifact>, ServiceError> {
        if self.get(compile_job_id).await?.is_none() {
            return Err(ServiceError::NotFound);
        }

        let rows = self
            .client
            .query(
                "SELECT * FROM compile_artifacts WHERE compile_job_id = $1 ORDER BY created_at ASC",
                &[&compile_job_id],
            )
            .await?;
        rows.into_iter().map(row_to_artifact).collect()
    }

    pub async fn get_artifact(
        &self,
        compile_job_id: Uuid,
        artifact_id: Uuid,
    ) -> Result<Option<CompileArtifact>, ServiceError> {
        let row = self
            .client
            .query_opt(
                "SELECT * FROM compile_artifacts WHERE compile_job_id = $1 AND id = $2",
                &[&compile_job_id, &artifact_id],
            )
            .await?;
        row.map(row_to_artifact).transpose()
    }

    pub async fn delete_artifact(
        &self,
        compile_job_id: Uuid,
        artifact_id: Uuid,
    ) -> Result<bool, ServiceError> {
        let deleted = self
            .client
            .execute(
                "DELETE FROM compile_artifacts WHERE compile_job_id = $1 AND id = $2",
                &[&compile_job_id, &artifact_id],
            )
            .await?;
        Ok(deleted > 0)
    }

    async fn compile_or_get_cached(
        &self,
        source: &str,
        options: &CompileOptions,
    ) -> Result<CompileOutput, ServiceError> {
        let cache_key = cache_key(source, options);
        if let Some(mut cached) = self.find_cached_output(&cache_key).await? {
            cached.cache_hit = true;
            return Ok(cached);
        }

        Ok(compile_source(source, options))
    }

    async fn find_cached_output(
        &self,
        cache_key: &str,
    ) -> Result<Option<CompileOutput>, ServiceError> {
        let row = self
            .client
            .query_opt(
                "SELECT emitted_javascript, source_map, diagnostics \
                 FROM compile_jobs \
                 WHERE cache_key = $1 AND status = 'succeeded' AND emitted_javascript IS NOT NULL \
                 ORDER BY updated_at DESC \
                 LIMIT 1",
                &[&cache_key],
            )
            .await?;

        row.map(|row| {
            let diagnostics: Value = row.get("diagnostics");
            Ok(CompileOutput {
                javascript: row
                    .get::<_, Option<String>>("emitted_javascript")
                    .unwrap_or_default(),
                source_map: row.get("source_map"),
                diagnostics: serde_json::from_value(diagnostics)?,
                cache_hit: false,
            })
        })
        .transpose()
    }

    async fn insert_job(
        &self,
        source: String,
        options: CompileOptions,
        status: CompileJobStatus,
        output: CompileOutput,
        cache_key: String,
    ) -> Result<CompileJob, ServiceError> {
        let id = Uuid::new_v4();
        let options_json = serde_json::to_value(&options)?;
        let diagnostics_json = serde_json::to_value(&output.diagnostics)?;
        let status_value = status.as_db_str();
        let row = self
            .client
            .query_one(
                "INSERT INTO compile_jobs \
                 (id, source, options, status, emitted_javascript, source_map, diagnostics, cache_key) \
                 VALUES ($1, $2, $3, $4, $5, $6, $7, $8) \
                 RETURNING *",
                &[
                    &id,
                    &source,
                    &options_json,
                    &status_value,
                    &output.javascript,
                    &output.source_map,
                    &diagnostics_json,
                    &cache_key,
                ],
            )
            .await?;
        self.replace_artifacts(id, &output).await?;
        row_to_job(row)
    }

    async fn update_job(
        &self,
        id: Uuid,
        source: String,
        options: CompileOptions,
        status: CompileJobStatus,
        output: CompileOutput,
        cache_key: String,
    ) -> Result<CompileJob, ServiceError> {
        let options_json = serde_json::to_value(&options)?;
        let diagnostics_json = serde_json::to_value(&output.diagnostics)?;
        let status_value = status.as_db_str();
        let row = self
            .client
            .query_one(
                "UPDATE compile_jobs \
                 SET source = $2, options = $3, status = $4, emitted_javascript = $5, \
                     source_map = $6, diagnostics = $7, cache_key = $8, updated_at = NOW() \
                 WHERE id = $1 \
                 RETURNING *",
                &[
                    &id,
                    &source,
                    &options_json,
                    &status_value,
                    &output.javascript,
                    &output.source_map,
                    &diagnostics_json,
                    &cache_key,
                ],
            )
            .await?;
        self.replace_artifacts(id, &output).await?;
        row_to_job(row)
    }

    async fn replace_artifacts(
        &self,
        compile_job_id: Uuid,
        output: &CompileOutput,
    ) -> Result<(), ServiceError> {
        self.client
            .execute(
                "DELETE FROM compile_artifacts WHERE compile_job_id = $1",
                &[&compile_job_id],
            )
            .await?;

        let artifacts = [
            (
                CompileArtifactType::Javascript,
                serde_json::json!({ "javascript": output.javascript }),
            ),
            (
                CompileArtifactType::SourceMap,
                serde_json::json!({ "sourceMap": output.source_map }),
            ),
            (
                CompileArtifactType::Diagnostics,
                serde_json::json!({ "diagnostics": output.diagnostics }),
            ),
        ];

        for (artifact_type, content) in artifacts {
            let id = Uuid::new_v4();
            let artifact_type = artifact_type.as_db_str();
            self.client
                .execute(
                    "INSERT INTO compile_artifacts (id, compile_job_id, artifact_type, content) \
                     VALUES ($1, $2, $3, $4)",
                    &[&id, &compile_job_id, &artifact_type, &content],
                )
                .await?;
        }

        Ok(())
    }

    fn validate_source(&self, source: &str) -> Result<(), ServiceError> {
        if source.trim().is_empty() {
            return Err(ServiceError::EmptySource);
        }

        if source.len() > self.max_source_bytes {
            return Err(ServiceError::SourceTooLarge {
                max: self.max_source_bytes,
            });
        }

        Ok(())
    }
}

pub fn compile_source(source: &str, options: &CompileOptions) -> CompileOutput {
    match transpiler_parser::parse_program(source) {
        Ok(program) => {
            let mut macros = MacroExpansionContext::default();
            let expanded = match macros.expand_program(program) {
                Ok(program) => program,
                Err(error) => {
                    return CompileOutput {
                        javascript: String::new(),
                        source_map: None,
                        diagnostics: vec![Diagnostic {
                            code: "MACRO001".to_string(),
                            severity: DiagnosticSeverity::Error,
                            message: error.to_string(),
                            span: None,
                        }],
                        cache_hit: false,
                    };
                }
            };

            let transformed = if options.optimize {
                TransformPipeline::optimized().run(expanded)
            } else {
                expanded
            };
            let emitted = JavaScriptEmitter::new(EmitOptions {
                source_file: "input.tsl".to_string(),
                output_file: "output.js".to_string(),
                source_maps: options.source_maps,
            })
            .emit_program(&transformed);

            CompileOutput {
                javascript: emitted.javascript,
                source_map: emitted.source_map,
                diagnostics: Vec::new(),
                cache_hit: false,
            }
        }
        Err(error) => CompileOutput {
            javascript: String::new(),
            source_map: None,
            diagnostics: vec![Diagnostic {
                code: "PARSER001".to_string(),
                severity: DiagnosticSeverity::Error,
                message: error.to_string(),
                span: error.span(),
            }],
            cache_hit: false,
        },
    }
}

pub fn validate_options(options: &CompileOptions) -> Result<(), ServiceError> {
    match options.target.as_str() {
        "es2020" | "es2021" | "es2022" | "esnext" => Ok(()),
        _ => Err(ServiceError::InvalidTarget),
    }
}

fn cache_key(source: &str, options: &CompileOptions) -> String {
    CacheKey::from_source(source, options, COMPILER_VERSION).digest
}

fn status_from_output(output: &CompileOutput) -> CompileJobStatus {
    if output
        .diagnostics
        .iter()
        .any(|diagnostic| diagnostic.severity == DiagnosticSeverity::Error)
    {
        CompileJobStatus::Failed
    } else {
        CompileJobStatus::Succeeded
    }
}

fn row_to_job(row: Row) -> Result<CompileJob, ServiceError> {
    let status: String = row.get("status");
    let options: Value = row.get("options");
    let diagnostics: Value = row.get("diagnostics");
    let output = CompileOutput {
        javascript: row
            .get::<_, Option<String>>("emitted_javascript")
            .unwrap_or_default(),
        source_map: row.get("source_map"),
        diagnostics: serde_json::from_value(diagnostics)?,
        cache_hit: false,
    };

    Ok(CompileJob {
        id: row.get("id"),
        source: row.get("source"),
        options: serde_json::from_value(options)?,
        output: Some(output),
        status: CompileJobStatus::from_db_str(&status)
            .ok_or(ServiceError::InvalidStatus(status))?,
        created_at: row.get::<_, DateTime<Utc>>("created_at"),
        updated_at: row.get::<_, DateTime<Utc>>("updated_at"),
    })
}

fn row_to_artifact(row: Row) -> Result<CompileArtifact, ServiceError> {
    let artifact_type: String = row.get("artifact_type");

    Ok(CompileArtifact {
        id: row.get("id"),
        compile_job_id: row.get("compile_job_id"),
        artifact_type: CompileArtifactType::from_db_str(&artifact_type)
            .ok_or(ServiceError::InvalidArtifactType(artifact_type))?,
        content: row.get("content"),
        created_at: row.get::<_, DateTime<Utc>>("created_at"),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn compile_source_emits_optimized_javascript() {
        let output = compile_source("let answer: number = 40 + 2;", &CompileOptions::default());

        assert!(output.diagnostics.is_empty());
        assert_eq!(output.javascript.trim(), "let answer = 42;");
        assert!(output.source_map.is_some());
    }

    #[test]
    fn compile_source_reports_parser_diagnostic() {
        let output = compile_source("let = ;", &CompileOptions::default());

        assert_eq!(output.diagnostics[0].code, "PARSER001");
        assert!(output.javascript.is_empty());
    }

    #[test]
    fn rejects_unknown_target() {
        let result = validate_options(&CompileOptions {
            target: "es5".to_string(),
            ..CompileOptions::default()
        });

        assert!(matches!(result, Err(ServiceError::InvalidTarget)));
    }
}
