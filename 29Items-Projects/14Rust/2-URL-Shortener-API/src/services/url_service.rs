use crate::{
    domain::url::{NewShortUrl, ShortUrl, UrlUpdate},
    error::AppError,
    infrastructure::{database::DbPool, repository::UrlRepository},
};
use chrono::{DateTime, NaiveDateTime, Utc};
use rand::{distributions::Alphanumeric, Rng};
use url::Url;

#[derive(Clone)]
pub struct UrlService {
    pool: DbPool,
    public_base_url: String,
}
impl UrlService {
    pub fn new(pool: DbPool, public_base_url: String) -> Self {
        Self {
            pool,
            public_base_url,
        }
    }
    pub fn create(
        &self,
        long_url: String,
        custom_code: Option<String>,
        expires_at: Option<DateTime<Utc>>,
    ) -> Result<ShortUrl, AppError> {
        validate_url(&long_url)?;
        let expires_at = validate_expiry(expires_at)?;
        let mut conn = self.connection()?;
        if let Some(code) = custom_code {
            validate_code(&code)?;
            return UrlRepository::insert(
                &mut conn,
                NewShortUrl {
                    short_code: &code,
                    long_url: &long_url,
                    status: "active",
                    expires_at,
                },
            );
        }
        for _ in 0..5 {
            let code = generate_code();
            match UrlRepository::insert(
                &mut conn,
                NewShortUrl {
                    short_code: &code,
                    long_url: &long_url,
                    status: "active",
                    expires_at,
                },
            ) {
                Err(AppError::Conflict) => continue,
                result => return result,
            }
        }
        Err(AppError::Conflict)
    }
    pub fn get(&self, code: &str) -> Result<ShortUrl, AppError> {
        validate_code(code)?;
        UrlRepository::find(&mut *self.connection()?, code)
    }
    pub fn list(&self, page: i64, per_page: i64) -> Result<Vec<ShortUrl>, AppError> {
        if page < 1 || !(1..=100).contains(&per_page) {
            return Err(AppError::Validation(
                "page must be >= 1 and per_page must be 1..100".into(),
            ));
        }
        let offset = page
            .checked_sub(1)
            .and_then(|value| value.checked_mul(per_page))
            .ok_or_else(|| AppError::Validation("pagination values are too large".into()))?;
        UrlRepository::list(&mut *self.connection()?, offset, per_page)
    }
    pub fn update(
        &self,
        code: &str,
        long_url: String,
        status: String,
        expires_at: Option<DateTime<Utc>>,
    ) -> Result<ShortUrl, AppError> {
        validate_code(code)?;
        validate_url(&long_url)?;
        if !matches!(status.as_str(), "active" | "disabled") {
            return Err(AppError::Validation(
                "status must be active or disabled".into(),
            ));
        }
        let expires_at = validate_expiry(expires_at)?;
        UrlRepository::update(
            &mut *self.connection()?,
            code,
            UrlUpdate {
                long_url,
                status,
                expires_at,
            },
        )
    }
    pub fn delete(&self, code: &str) -> Result<(), AppError> {
        validate_code(code)?;
        UrlRepository::delete(&mut *self.connection()?, code)
    }
    pub fn resolve(&self, code: &str) -> Result<ShortUrl, AppError> {
        validate_code(code)?;
        UrlRepository::resolve_and_increment(&mut *self.connection()?, code)
    }
    pub fn short_url(&self, code: &str) -> String {
        format!("{}/{}", self.public_base_url, code)
    }
    pub fn health(&self) -> Result<(), AppError> {
        UrlRepository::health(&mut *self.connection()?)
    }
    fn connection(
        &self,
    ) -> Result<
        diesel::r2d2::PooledConnection<diesel::r2d2::ConnectionManager<diesel::SqliteConnection>>,
        AppError,
    > {
        self.pool.get().map_err(|error| {
            tracing::error!(error = %error, "failed to acquire database connection");
            AppError::Internal
        })
    }
}
fn generate_code() -> String {
    rand::thread_rng()
        .sample_iter(&Alphanumeric)
        .take(7)
        .map(char::from)
        .collect()
}
fn validate_code(code: &str) -> Result<(), AppError> {
    if code.len() < 3
        || code.len() > 32
        || !code
            .bytes()
            .all(|c| c.is_ascii_alphanumeric() || c == b'-' || c == b'_')
    {
        return Err(AppError::Validation(
            "short code must be 3-32 URL-safe characters".into(),
        ));
    }
    if matches!(code.to_ascii_lowercase().as_str(), "api" | "health") {
        return Err(AppError::Validation("short code is reserved".into()));
    }
    Ok(())
}
fn validate_url(raw: &str) -> Result<(), AppError> {
    if raw.is_empty() || raw.trim() != raw || raw.len() > 2_048 || raw.chars().any(char::is_control)
    {
        return Err(AppError::Validation(
            "long_url must be non-empty, contain no whitespace padding or control characters, and be at most 2048 bytes".into(),
        ));
    }
    let parsed = Url::parse(raw)
        .map_err(|_| AppError::Validation("long_url must be an absolute URL".into()))?;
    if !matches!(parsed.scheme(), "http" | "https")
        || parsed.host_str().is_none()
        || !parsed.username().is_empty()
        || parsed.password().is_some()
    {
        return Err(AppError::Validation(
            "long_url must be an http(s) URL with a host and no embedded credentials".into(),
        ));
    }
    Ok(())
}
fn validate_expiry(expiry: Option<DateTime<Utc>>) -> Result<Option<NaiveDateTime>, AppError> {
    if let Some(value) = expiry {
        if value <= Utc::now() {
            return Err(AppError::Validation(
                "expires_at must be in the future".into(),
            ));
        }
        Ok(Some(value.naive_utc()))
    } else {
        Ok(None)
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn code_and_url_validation_reject_bad_input() {
        assert!(validate_code("ab").is_err());
        assert!(validate_url("file:///x").is_err());
        assert!(validate_url("https://user:password@example.com").is_err());
        assert!(validate_url("https://example.com/\npath").is_err());
    }

    #[test]
    fn validation_accepts_supported_boundaries() {
        assert!(validate_code("abc").is_ok());
        assert!(validate_code(&"a".repeat(32)).is_ok());
        assert!(validate_url("https://example.com/path?q=value#fragment").is_ok());
    }

    #[test]
    fn validation_rejects_reserved_codes_whitespace_and_past_expiry() {
        assert!(validate_code("API").is_err());
        assert!(validate_code("invalid/code").is_err());
        assert!(validate_url(" https://example.com").is_err());
        assert!(validate_url(&format!("https://example.com/{}", "x".repeat(2_048))).is_err());
        assert!(validate_expiry(Some(Utc::now() - chrono::Duration::seconds(1))).is_err());
    }
}
