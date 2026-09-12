use serde_json::Value;
use std::collections::{HashMap, HashSet};

#[derive(Default, Debug)]
pub struct SchemaInferencer {
    /// Maps a JSON pointer (e.g. "/user/id") to a set of observed types
    type_map: HashMap<String, HashSet<String>>,
}

impl SchemaInferencer {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn observe(&mut self, value: &Value) {
        self.traverse(value, String::new());
    }

    fn traverse(&mut self, value: &Value, current_path: String) {
        let type_name = match value {
            Value::Null => "Null",
            Value::Bool(_) => "Boolean",
            Value::Number(n) if n.is_f64() => "Float",
            Value::Number(_) => "Integer",
            Value::String(_) => "String",
            Value::Array(_) => "Array",
            Value::Object(_) => "Object",
        };

        // Record the type for this path
        self.type_map
            .entry(current_path.clone())
            .or_insert_with(HashSet::new)
            .insert(type_name.to_string());

        // Recurse into objects and arrays
        match value {
            Value::Object(map) => {
                for (k, v) in map {
                    let next_path = if current_path.is_empty() {
                        k.clone()
                    } else {
                        format!("{}.{}", current_path, k)
                    };
                    self.traverse(v, next_path);
                }
            }
            Value::Array(arr) => {
                let next_path = if current_path.is_empty() {
                    "[]".to_string()
                } else {
                    format!("{}[]", current_path)
                };
                // For arrays, we just observe elements under the array path
                for item in arr {
                    self.traverse(item, next_path.clone());
                }
            }
            _ => {}
        }
    }

    pub fn generate_report(&self) -> Value {
        let mut report = serde_json::Map::new();
        for (path, types) in &self.type_map {
            let path_key = if path.is_empty() { "(root)" } else { path };
            
            let mut types_vec: Vec<String> = types.iter().cloned().collect();
            types_vec.sort(); // Deterministic output
            
            let types_val = if types_vec.len() == 1 {
                Value::String(types_vec[0].clone())
            } else {
                Value::Array(types_vec.into_iter().map(Value::String).collect())
            };
            
            report.insert(path_key.to_string(), types_val);
        }
        Value::Object(report)
    }
}
