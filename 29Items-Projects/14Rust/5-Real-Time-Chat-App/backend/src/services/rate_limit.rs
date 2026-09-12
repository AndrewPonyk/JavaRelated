use std::{
    sync::Arc,
    time::{Duration, Instant},
};

use dashmap::DashMap;

use crate::error::AppError;

#[derive(Debug, Clone)]
pub struct RateLimiter {
    entries: Arc<DashMap<String, Window>>,
    window: Duration,
}

#[derive(Debug, Clone, Copy)]
struct Window {
    started: Instant,
    count: u32,
}

impl RateLimiter {
    pub fn per_minute() -> Self {
        Self {
            entries: Arc::new(DashMap::new()),
            window: Duration::from_secs(60),
        }
    }

    pub fn check(&self, key: impl Into<String>, limit: u32) -> Result<(), AppError> {
        const PRUNE_THRESHOLD: usize = 10_000;
        const MAX_ENTRIES: usize = 100_000;
        let now = Instant::now();
        let key = key.into();
        if self.entries.len() >= PRUNE_THRESHOLD {
            self.entries
                .retain(|_, window| now.duration_since(window.started) < self.window);
        }
        if self.entries.len() >= MAX_ENTRIES && !self.entries.contains_key(&key) {
            return Err(AppError::RateLimited {
                retry_after_seconds: self.window.as_secs(),
            });
        }
        let mut entry = self.entries.entry(key).or_insert(Window {
            started: now,
            count: 0,
        });
        if now.duration_since(entry.started) >= self.window {
            *entry = Window {
                started: now,
                count: 0,
            };
        }
        if entry.count >= limit {
            let retry_after_seconds = self
                .window
                .saturating_sub(now.duration_since(entry.started))
                .as_secs()
                .max(1);
            return Err(AppError::RateLimited {
                retry_after_seconds,
            });
        }
        entry.count += 1;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_requests_after_limit() {
        let limiter = RateLimiter::per_minute();
        assert!(limiter.check("user:1", 1).is_ok());
        assert!(matches!(
            limiter.check("user:1", 1),
            Err(AppError::RateLimited { .. })
        ));
        assert!(limiter.check("user:2", 1).is_ok());
    }
}
