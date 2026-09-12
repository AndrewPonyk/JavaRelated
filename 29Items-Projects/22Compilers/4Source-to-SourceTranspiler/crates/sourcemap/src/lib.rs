use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SourceMapSegment {
    pub generated_line: u32,
    pub generated_column: u32,
    pub source_index: u32,
    pub original_line: u32,
    pub original_column: u32,
    pub name: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SourceMap {
    pub version: u8,
    pub file: String,
    pub sources: Vec<String>,
    pub sources_content: Vec<String>,
    pub names: Vec<String>,
    pub mappings: String,
    pub segments: Vec<SourceMapSegment>,
}

impl SourceMap {
    pub fn empty(file: impl Into<String>) -> Self {
        Self {
            version: 3,
            file: file.into(),
            sources: Vec::new(),
            sources_content: Vec::new(),
            names: Vec::new(),
            mappings: String::new(),
            segments: Vec::new(),
        }
    }

    pub fn to_json(&self) -> serde_json::Result<serde_json::Value> {
        serde_json::to_value(self)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn empty_source_map_uses_v3_shape() {
        let map = SourceMap::empty("output.js");
        let json = map.to_json().expect("source map json");

        assert_eq!(json["version"], 3);
        assert_eq!(json["file"], "output.js");
    }
}
