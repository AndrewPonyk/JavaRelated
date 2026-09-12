use std::collections::HashMap;

use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use transpiler_core::CompileOptions;

#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CacheKey {
    pub compiler_version: String,
    pub digest: String,
}

impl CacheKey {
    pub fn from_source(source: &str, options: &CompileOptions, compiler_version: &str) -> Self {
        let mut hasher = Sha256::new();
        hasher.update(compiler_version.as_bytes());
        hasher.update(source.as_bytes());
        hasher.update(serde_json::to_vec(options).unwrap_or_default());

        Self {
            compiler_version: compiler_version.to_string(),
            digest: format!("{:x}", hasher.finalize()),
        }
    }
}

pub trait IncrementalCache {
    type Value: Clone;

    fn get(&self, key: &CacheKey) -> Option<Self::Value>;
    fn put(&mut self, key: CacheKey, value: Self::Value);
}

#[derive(Debug, Clone, Default)]
pub struct MemoryIncrementalCache<T: Clone> {
    values: HashMap<CacheKey, T>,
}

impl<T: Clone> IncrementalCache for MemoryIncrementalCache<T> {
    type Value = T;

    fn get(&self, key: &CacheKey) -> Option<Self::Value> {
        self.values.get(key).cloned()
    }

    fn put(&mut self, key: CacheKey, value: Self::Value) {
        self.values.insert(key, value);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn cache_key_changes_when_options_change() {
        let source = "let answer = 42;";
        let first = CacheKey::from_source(source, &CompileOptions::default(), "1");
        let second = CacheKey::from_source(
            source,
            &CompileOptions {
                optimize: false,
                ..CompileOptions::default()
            },
            "1",
        );

        assert_ne!(first.digest, second.digest);
    }

    #[test]
    fn memory_cache_round_trips_value() {
        let key = CacheKey::from_source("let x = 1;", &CompileOptions::default(), "1");
        let mut cache = MemoryIncrementalCache::default();

        cache.put(key.clone(), "compiled".to_string());

        assert_eq!(cache.get(&key), Some("compiled".to_string()));
    }
}
