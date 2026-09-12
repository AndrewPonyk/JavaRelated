use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use transpiler_core::{CompileOptions, CompileOutput};
use uuid::Uuid;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum CompileJobStatus {
    Pending,
    Succeeded,
    Failed,
}

impl CompileJobStatus {
    pub fn as_db_str(&self) -> &'static str {
        match self {
            Self::Pending => "pending",
            Self::Succeeded => "succeeded",
            Self::Failed => "failed",
        }
    }

    pub fn from_db_str(value: &str) -> Option<Self> {
        match value {
            "pending" => Some(Self::Pending),
            "succeeded" => Some(Self::Succeeded),
            "failed" => Some(Self::Failed),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CompileJob {
    pub id: Uuid,
    pub source: String,
    pub options: CompileOptions,
    pub output: Option<CompileOutput>,
    pub status: CompileJobStatus,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum CompileArtifactType {
    Javascript,
    SourceMap,
    Diagnostics,
}

impl CompileArtifactType {
    pub fn as_db_str(&self) -> &'static str {
        match self {
            Self::Javascript => "javascript",
            Self::SourceMap => "source_map",
            Self::Diagnostics => "diagnostics",
        }
    }

    pub fn from_db_str(value: &str) -> Option<Self> {
        match value {
            "javascript" => Some(Self::Javascript),
            "source_map" => Some(Self::SourceMap),
            "diagnostics" => Some(Self::Diagnostics),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CompileArtifact {
    pub id: Uuid,
    pub compile_job_id: Uuid,
    pub artifact_type: CompileArtifactType,
    pub content: serde_json::Value,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateCompileJobRequest {
    pub source: String,
    #[serde(default)]
    pub options: CompileOptions,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateCompileJobRequest {
    pub source: Option<String>,
    pub options: Option<CompileOptions>,
}
