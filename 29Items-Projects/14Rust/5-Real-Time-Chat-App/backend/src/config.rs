use std::{collections::HashMap, env, net::IpAddr, path::PathBuf, str::FromStr, time::Duration};

use thiserror::Error;

#[derive(Debug, Clone)]
pub struct Settings {
    pub environment: String,
    pub host: IpAddr,
    pub port: u16,
    pub database_url: String,
    pub database_max_connections: u32,
    pub static_dir: PathBuf,
    pub allowed_origins: Vec<String>,
    pub chat_channel_capacity: usize,
    pub max_message_bytes: usize,
    pub max_connections_per_room: usize,
    pub shutdown_timeout: Duration,
    pub session_ttl: Duration,
    pub secure_cookies: bool,
    pub max_list_page_size: usize,
    pub max_history_page_size: usize,
    pub password_hash_concurrency: usize,
    pub messages_per_minute: u32,
    pub api_requests_per_minute: u32,
    pub heartbeat_interval: Duration,
    pub heartbeat_timeout: Duration,
}

#[derive(Debug, Error)]
pub enum ConfigError {
    #[error("missing required environment variable {0}")]
    Missing(&'static str),
    #[error("invalid value for {name}: {value}")]
    Invalid { name: &'static str, value: String },
}

impl Settings {
    pub fn from_env() -> Result<Self, ConfigError> {
        let values = env::vars().collect();
        Self::from_values(&values)
    }

    fn from_values(values: &HashMap<String, String>) -> Result<Self, ConfigError> {
        let environment = optional(values, "APP_ENV", "development");
        let settings = Self {
            environment: environment.clone(),
            host: parse(values, "APP_HOST", "0.0.0.0")?,
            port: parse(values, "APP_PORT", "8080")?,
            database_url: values
                .get("DATABASE_URL")
                .cloned()
                .ok_or(ConfigError::Missing("DATABASE_URL"))?,
            database_max_connections: bounded(
                parse(values, "DATABASE_MAX_CONNECTIONS", "10")?,
                "DATABASE_MAX_CONNECTIONS",
                1,
                100,
            )?,
            static_dir: PathBuf::from(optional(values, "STATIC_DIR", "frontend")),
            allowed_origins: optional(values, "ALLOWED_ORIGINS", "http://localhost:8080")
                .split(',')
                .map(str::trim)
                .filter(|origin| !origin.is_empty())
                .map(str::to_owned)
                .collect(),
            chat_channel_capacity: bounded(
                parse(values, "CHAT_CHANNEL_CAPACITY", "256")?,
                "CHAT_CHANNEL_CAPACITY",
                8,
                65_536,
            )?,
            max_message_bytes: bounded(
                parse(values, "MAX_MESSAGE_BYTES", "4096")?,
                "MAX_MESSAGE_BYTES",
                1,
                4096,
            )?,
            max_connections_per_room: bounded(
                parse(values, "MAX_CONNECTIONS_PER_ROOM", "500")?,
                "MAX_CONNECTIONS_PER_ROOM",
                1,
                100_000,
            )?,
            shutdown_timeout: Duration::from_secs(bounded(
                parse(values, "SHUTDOWN_TIMEOUT_SECONDS", "20")?,
                "SHUTDOWN_TIMEOUT_SECONDS",
                1,
                300,
            )?),
            session_ttl: Duration::from_secs(
                bounded(
                    parse::<u64>(values, "SESSION_TTL_HOURS", "24")?,
                    "SESSION_TTL_HOURS",
                    1,
                    720,
                )? * 3600,
            ),
            secure_cookies: parse(
                values,
                "SECURE_COOKIES",
                if environment == "production" || environment == "staging" {
                    "true"
                } else {
                    "false"
                },
            )?,
            max_list_page_size: bounded(
                parse(values, "MAX_LIST_PAGE_SIZE", "100")?,
                "MAX_LIST_PAGE_SIZE",
                1,
                500,
            )?,
            max_history_page_size: bounded(
                parse(values, "MAX_HISTORY_PAGE_SIZE", "100")?,
                "MAX_HISTORY_PAGE_SIZE",
                1,
                500,
            )?,
            password_hash_concurrency: bounded(
                parse(values, "PASSWORD_HASH_CONCURRENCY", "4")?,
                "PASSWORD_HASH_CONCURRENCY",
                1,
                32,
            )?,
            messages_per_minute: bounded(
                parse(values, "MESSAGES_PER_MINUTE", "60")?,
                "MESSAGES_PER_MINUTE",
                1,
                10_000,
            )?,
            api_requests_per_minute: bounded(
                parse(values, "API_REQUESTS_PER_MINUTE", "300")?,
                "API_REQUESTS_PER_MINUTE",
                1,
                100_000,
            )?,
            heartbeat_interval: Duration::from_secs(bounded(
                parse(values, "HEARTBEAT_INTERVAL_SECONDS", "20")?,
                "HEARTBEAT_INTERVAL_SECONDS",
                5,
                120,
            )?),
            heartbeat_timeout: Duration::from_secs(bounded(
                parse(values, "HEARTBEAT_TIMEOUT_SECONDS", "60")?,
                "HEARTBEAT_TIMEOUT_SECONDS",
                10,
                600,
            )?),
        };
        settings.validate()?;
        Ok(settings)
    }

    pub fn bind_address(&self) -> (IpAddr, u16) {
        (self.host, self.port)
    }

    fn validate(&self) -> Result<(), ConfigError> {
        if !matches!(
            self.environment.as_str(),
            "development" | "test" | "staging" | "production"
        ) {
            return Err(invalid("APP_ENV", &self.environment));
        }
        if !self.database_url.starts_with("postgres://")
            && !self.database_url.starts_with("postgresql://")
        {
            return Err(invalid("DATABASE_URL", "invalid PostgreSQL URL"));
        }
        if self.allowed_origins.is_empty() {
            return Err(invalid("ALLOWED_ORIGINS", ""));
        }
        for origin in &self.allowed_origins {
            let uri = origin
                .parse::<http::Uri>()
                .map_err(|_| invalid("ALLOWED_ORIGINS", origin))?;
            let valid_scheme = matches!(uri.scheme_str(), Some("http" | "https"));
            let valid_authority = uri
                .authority()
                .is_some_and(|authority| !authority.as_str().contains('@'));
            let valid_path = uri.path() == "/" && uri.query().is_none();
            if !valid_scheme || !valid_authority || !valid_path {
                return Err(invalid("ALLOWED_ORIGINS", origin));
            }
        }
        if self.heartbeat_timeout <= self.heartbeat_interval {
            return Err(invalid(
                "HEARTBEAT_TIMEOUT_SECONDS",
                &self.heartbeat_timeout.as_secs().to_string(),
            ));
        }
        if self.environment == "production" || self.environment == "staging" {
            if !self.secure_cookies {
                return Err(invalid("SECURE_COOKIES", "false"));
            }
            if self
                .allowed_origins
                .iter()
                .any(|origin| !origin.starts_with("https://"))
            {
                return Err(invalid("ALLOWED_ORIGINS", &self.allowed_origins.join(",")));
            }
        }
        Ok(())
    }
}

fn optional(values: &HashMap<String, String>, name: &'static str, default: &str) -> String {
    values
        .get(name)
        .cloned()
        .unwrap_or_else(|| default.to_owned())
}

fn parse<T>(
    values: &HashMap<String, String>,
    name: &'static str,
    default: &str,
) -> Result<T, ConfigError>
where
    T: FromStr,
{
    let value = optional(values, name, default);
    value
        .parse()
        .map_err(|_| ConfigError::Invalid { name, value })
}

fn bounded<T>(value: T, name: &'static str, minimum: T, maximum: T) -> Result<T, ConfigError>
where
    T: PartialOrd + ToString + Copy,
{
    if value < minimum || value > maximum {
        return Err(invalid(name, &value.to_string()));
    }
    Ok(value)
}

fn invalid(name: &'static str, value: &str) -> ConfigError {
    ConfigError::Invalid {
        name,
        value: value.to_owned(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn valid_values() -> HashMap<String, String> {
        HashMap::from([(
            "DATABASE_URL".to_owned(),
            "postgres://chat:chat@localhost/chat".to_owned(),
        )])
    }

    #[test]
    fn defaults_are_bounded_and_development_safe() {
        let settings = Settings::from_values(&valid_values()).unwrap();
        assert_eq!(settings.port, 8080);
        assert_eq!(settings.session_ttl, Duration::from_secs(86_400));
        assert!(!settings.secure_cookies);
    }

    #[test]
    fn rejects_zero_and_inverted_heartbeat_values() {
        let mut values = valid_values();
        values.insert("DATABASE_MAX_CONNECTIONS".to_owned(), "0".to_owned());
        assert!(Settings::from_values(&values).is_err());

        values.insert("DATABASE_MAX_CONNECTIONS".to_owned(), "5".to_owned());
        values.insert("HEARTBEAT_INTERVAL_SECONDS".to_owned(), "30".to_owned());
        values.insert("HEARTBEAT_TIMEOUT_SECONDS".to_owned(), "20".to_owned());
        assert!(Settings::from_values(&values).is_err());
    }

    #[test]
    fn production_requires_secure_https_origins() {
        let mut values = valid_values();
        values.insert("APP_ENV".to_owned(), "production".to_owned());
        values.insert(
            "ALLOWED_ORIGINS".to_owned(),
            "http://chat.example.com".to_owned(),
        );
        assert!(Settings::from_values(&values).is_err());

        values.insert(
            "ALLOWED_ORIGINS".to_owned(),
            "https://chat.example.com/path".to_owned(),
        );
        assert!(Settings::from_values(&values).is_err());
    }
}
