use serde::{Deserialize, Serialize};
use thiserror::Error;

use crate::ast::SourceSpan;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum DiagnosticSeverity {
    Info,
    Warning,
    Error,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Diagnostic {
    pub code: String,
    pub severity: DiagnosticSeverity,
    pub message: String,
    pub span: Option<SourceSpan>,
}

#[derive(Debug, Error)]
pub enum TranspilerError {
    #[error("parse error: {0}")]
    Parse(String),
    #[error("transform error: {0}")]
    Transform(String),
    #[error("emit error: {0}")]
    Emit(String),
}
