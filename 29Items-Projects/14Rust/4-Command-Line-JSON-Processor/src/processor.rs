use std::path::Path;
use tokio::io::AsyncBufReadExt;
use serde_json::Value;

use crate::error::ProcessorError;
use crate::utils::get_reader;
use crate::schema::SchemaInferencer;

pub async fn process_json_stream(
    input_path: &Path,
    filter_expr: &str,
    schema_mode: bool,
    pretty: bool,
) -> Result<(), ProcessorError> {
    tracing::info!("Starting JSON processing stream");

    let mut reader = get_reader(input_path).await?;
    let mut line = String::new();
    let mut inferencer = SchemaInferencer::new();

    loop {
        line.clear();
        let bytes_read = reader.read_line(&mut line).await?;
        if bytes_read == 0 {
            break; // EOF
        }

        let trimmed = line.trim();
        if trimmed.is_empty() {
            continue;
        }

        // Parse line as JSON
        let value: Value = serde_json::from_str(trimmed)
            .map_err(|e| ProcessorError::Json(e))?;

        if schema_mode {
            inferencer.observe(&value);
        } else {
            // Apply filter
            if let Some(filtered) = apply_filter(&value, filter_expr) {
                if pretty {
                    println!("{}", serde_json::to_string_pretty(&filtered)?);
                } else {
                    println!("{}", serde_json::to_string(&filtered)?);
                }
            }
        }
    }

    if schema_mode {
        let report = inferencer.generate_report();
        if pretty {
            println!("{}", serde_json::to_string_pretty(&report)?);
        } else {
            println!("{}", serde_json::to_string(&report)?);
        }
    }

    Ok(())
}

/// A very simple jq-like filter evaluator
/// Supports:
/// - "." (identity)
/// - ".key" (extract property)
/// - ".key == value" (filter records)
fn apply_filter(value: &Value, expr: &str) -> Option<Value> {
    let expr = expr.trim();
    if expr == "." || expr.is_empty() {
        return Some(value.clone());
    }

    // Very basic dot-notation extraction: ".key"
    if expr.starts_with('.') && !expr.contains("==") {
        let key = &expr[1..];
        if let Value::Object(map) = value {
            return map.get(key).cloned();
        }
        return None;
    }

    // Very basic equality: ".key == \"val\"" or ".key == 10"
    if let Some(idx) = expr.find("==") {
        let lhs = expr[..idx].trim();
        let rhs = expr[idx + 2..].trim();

        if lhs.starts_with('.') {
            let key = &lhs[1..];
            if let Value::Object(map) = value {
                if let Some(field_val) = map.get(key) {
                    // Try to parse rhs as JSON to compare properly
                    if let Ok(rhs_val) = serde_json::from_str::<Value>(rhs) {
                        if field_val == &rhs_val {
                            return Some(value.clone()); // Return full object if matches
                        }
                    }
                }
            }
        }
        return None;
    }

    // Fallback: don't filter out, but we could return None if invalid
    Some(value.clone())
}
