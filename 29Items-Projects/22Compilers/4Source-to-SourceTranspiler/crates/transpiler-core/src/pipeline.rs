use serde::{Deserialize, Serialize};

use crate::diagnostics::Diagnostic;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CompileOptions {
    pub optimize: bool,
    pub source_maps: bool,
    pub target: String,
}

impl Default for CompileOptions {
    fn default() -> Self {
        Self {
            optimize: true,
            source_maps: true,
            target: "es2022".to_string(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CompileOutput {
    pub javascript: String,
    pub source_map: Option<serde_json::Value>,
    pub diagnostics: Vec<Diagnostic>,
    pub cache_hit: bool,
}
