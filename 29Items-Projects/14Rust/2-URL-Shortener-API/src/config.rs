use std::{env, net::SocketAddr};
use url::Url;

#[derive(Clone)]
pub struct Settings {
    pub bind_address: String,
    pub database_url: String,
    pub public_base_url: String,
    pub admin_api_token: String,
    pub cors_allowed_origins: Vec<String>,
    pub creation_rate_window_seconds: u64,
    pub creation_rate_limit: u32,
    pub creation_rate_max_clients: usize,
    pub trust_proxy_headers: bool,
    pub enable_hsts: bool,
}

impl Settings {
    pub fn from_env() -> Result<Self, String> {
        Self::from_lookup(|name| env::var(name).ok())
    }

    fn from_lookup(lookup: impl Fn(&str) -> Option<String>) -> Result<Self, String> {
        let bind_address = lookup("BIND_ADDRESS").unwrap_or_else(|| "127.0.0.1:8080".into());
        bind_address
            .parse::<SocketAddr>()
            .map_err(|_| "BIND_ADDRESS must be a valid IP address and port".to_string())?;

        let database_url = lookup("DATABASE_URL").unwrap_or_else(|| "url_shortener.db".into());
        if database_url.trim().is_empty() || database_url.trim() != database_url {
            return Err(
                "DATABASE_URL must be non-empty and contain no surrounding whitespace".into(),
            );
        }

        let public_base_url =
            lookup("PUBLIC_BASE_URL").unwrap_or_else(|| "http://localhost:8080".into());
        let public_base_url = validate_origin("PUBLIC_BASE_URL", &public_base_url)?;

        let admin_api_token = lookup("ADMIN_API_TOKEN").ok_or_else(|| {
            "ADMIN_API_TOKEN is required and must not use a default value".to_string()
        })?;
        if !(32..=4_096).contains(&admin_api_token.len())
            || !admin_api_token
                .bytes()
                .all(|byte| (b'!'..=b'~').contains(&byte))
        {
            return Err(
                "ADMIN_API_TOKEN must contain 32-4096 printable ASCII characters without spaces"
                    .into(),
            );
        }

        let cors_value =
            lookup("CORS_ALLOWED_ORIGINS").unwrap_or_else(|| "http://localhost:3000".into());
        let cors_allowed_origins = cors_value
            .split(',')
            .map(str::trim)
            .filter(|origin| !origin.is_empty())
            .map(|origin| validate_origin("CORS_ALLOWED_ORIGINS", origin))
            .collect::<Result<Vec<_>, _>>()?;
        if cors_allowed_origins.is_empty() {
            return Err("CORS_ALLOWED_ORIGINS must contain at least one origin".into());
        }

        let creation_rate_window_seconds = parse_bounded(
            "CREATION_RATE_WINDOW_SECONDS",
            lookup("CREATION_RATE_WINDOW_SECONDS"),
            60_u64,
            1,
            86_400,
        )?;
        let creation_rate_limit = parse_bounded(
            "CREATION_RATE_LIMIT",
            lookup("CREATION_RATE_LIMIT"),
            30_u32,
            1,
            100_000,
        )?;
        let creation_rate_max_clients = parse_bounded(
            "CREATION_RATE_MAX_CLIENTS",
            lookup("CREATION_RATE_MAX_CLIENTS"),
            10_000_usize,
            100,
            1_000_000,
        )?;
        let trust_proxy_headers =
            parse_bool("TRUST_PROXY_HEADERS", lookup("TRUST_PROXY_HEADERS"), false)?;
        let enable_hsts = parse_bool("ENABLE_HSTS", lookup("ENABLE_HSTS"), false)?;
        if enable_hsts && !public_base_url.starts_with("https://") {
            return Err("PUBLIC_BASE_URL must use https when ENABLE_HSTS=true".into());
        }

        Ok(Self {
            bind_address,
            database_url,
            public_base_url,
            admin_api_token,
            cors_allowed_origins,
            creation_rate_window_seconds,
            creation_rate_limit,
            creation_rate_max_clients,
            trust_proxy_headers,
            enable_hsts,
        })
    }
}

fn validate_origin(name: &str, raw: &str) -> Result<String, String> {
    let parsed = Url::parse(raw).map_err(|_| format!("{name} must contain valid URL origins"))?;
    if !matches!(parsed.scheme(), "http" | "https")
        || parsed.host_str().is_none()
        || !parsed.username().is_empty()
        || parsed.password().is_some()
        || parsed.path() != "/"
        || parsed.query().is_some()
        || parsed.fragment().is_some()
    {
        return Err(format!(
            "{name} entries must be http(s) origins without credentials, paths, queries, or fragments"
        ));
    }
    Ok(parsed.origin().ascii_serialization())
}

fn parse_bounded<T>(
    name: &str,
    raw: Option<String>,
    default: T,
    minimum: T,
    maximum: T,
) -> Result<T, String>
where
    T: Copy + PartialOrd + std::str::FromStr,
{
    let value = match raw {
        Some(raw) => raw
            .parse::<T>()
            .map_err(|_| format!("{name} must be a number"))?,
        None => default,
    };
    if value < minimum || value > maximum {
        return Err(format!("{name} is outside the supported range"));
    }
    Ok(value)
}

fn parse_bool(name: &str, raw: Option<String>, default: bool) -> Result<bool, String> {
    match raw.as_deref() {
        None => Ok(default),
        Some("true") | Some("1") => Ok(true),
        Some("false") | Some("0") => Ok(false),
        Some(_) => Err(format!("{name} must be true, false, 1, or 0")),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    fn settings(values: &[(&str, &str)]) -> Result<Settings, String> {
        let mut environment = HashMap::from([(
            "ADMIN_API_TOKEN".to_string(),
            "a-secure-test-token-with-32-characters".to_string(),
        )]);
        environment.extend(
            values
                .iter()
                .map(|(key, value)| ((*key).to_string(), (*value).to_string())),
        );
        Settings::from_lookup(|name| environment.get(name).cloned())
    }

    #[test]
    fn safe_defaults_are_valid() {
        let value = settings(&[]).expect("valid defaults");
        assert_eq!(value.bind_address, "127.0.0.1:8080");
        assert_eq!(value.creation_rate_limit, 30);
        assert_eq!(value.creation_rate_max_clients, 10_000);
        assert!(!value.trust_proxy_headers);
        assert!(!value.enable_hsts);
    }

    #[test]
    fn origins_and_operational_limits_are_normalized() {
        let value = settings(&[
            ("PUBLIC_BASE_URL", "https://short.example:443"),
            (
                "CORS_ALLOWED_ORIGINS",
                "https://app.example, http://localhost:3000",
            ),
            ("CREATION_RATE_WINDOW_SECONDS", "120"),
            ("CREATION_RATE_LIMIT", "50"),
            ("CREATION_RATE_MAX_CLIENTS", "20000"),
            ("TRUST_PROXY_HEADERS", "true"),
            ("ENABLE_HSTS", "true"),
        ])
        .expect("valid settings");
        assert_eq!(value.public_base_url, "https://short.example");
        assert_eq!(value.cors_allowed_origins.len(), 2);
        assert_eq!(value.creation_rate_window_seconds, 120);
        assert_eq!(value.creation_rate_limit, 50);
        assert_eq!(value.creation_rate_max_clients, 20_000);
        assert!(value.trust_proxy_headers);
    }

    #[test]
    fn invalid_security_sensitive_settings_fail_fast() {
        for values in [
            vec![("BIND_ADDRESS", "localhost:8080")],
            vec![("PUBLIC_BASE_URL", "https://example.com/path")],
            vec![("CORS_ALLOWED_ORIGINS", "*")],
            vec![("ADMIN_API_TOKEN", "too-short")],
            vec![(
                "ADMIN_API_TOKEN",
                "invalid token containing spaces but still long enough",
            )],
            vec![("CREATION_RATE_LIMIT", "0")],
            vec![("CREATION_RATE_MAX_CLIENTS", "99")],
            vec![("ENABLE_HSTS", "yes")],
            vec![("TRUST_PROXY_HEADERS", "yes")],
            vec![
                ("ENABLE_HSTS", "true"),
                ("PUBLIC_BASE_URL", "http://short.example"),
            ],
        ] {
            assert!(settings(&values).is_err(), "{values:?} should be rejected");
        }
    }
}
