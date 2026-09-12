use axum::{
    Json,
    extract::State,
    http::{
        HeaderMap, HeaderValue, StatusCode,
        header::{COOKIE, SET_COOKIE, USER_AGENT},
    },
};
use serde::Deserialize;

use crate::{domain::SessionIdentity, error::AppError, state::AppState};

use super::json::ApiJson;

const SESSION_COOKIE: &str = "chat_session";

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct CredentialsRequest {
    username: String,
    password: String,
}

pub async fn register(
    State(state): State<AppState>,
    headers: HeaderMap,
    ApiJson(payload): ApiJson<CredentialsRequest>,
) -> Result<(StatusCode, HeaderMap, Json<SessionIdentity>), AppError> {
    enforce_origin(&state, &headers)?;
    state.rate_limiter.check(
        request_rate_key(&headers, "register", &payload.username),
        state.settings.api_requests_per_minute.min(20),
    )?;
    let session = state
        .auth
        .register(&payload.username, &payload.password, user_agent(&headers))
        .await?;
    Ok((
        StatusCode::CREATED,
        session_headers(&state, &session.token)?,
        Json(session.identity),
    ))
}

pub async fn login(
    State(state): State<AppState>,
    headers: HeaderMap,
    ApiJson(payload): ApiJson<CredentialsRequest>,
) -> Result<(HeaderMap, Json<SessionIdentity>), AppError> {
    enforce_origin(&state, &headers)?;
    state.rate_limiter.check(
        request_rate_key(&headers, "login", &payload.username),
        state.settings.api_requests_per_minute.min(30),
    )?;
    let session = state
        .auth
        .login(&payload.username, &payload.password, user_agent(&headers))
        .await?;
    Ok((
        session_headers(&state, &session.token)?,
        Json(session.identity),
    ))
}

pub async fn logout(
    State(state): State<AppState>,
    headers: HeaderMap,
) -> Result<(StatusCode, HeaderMap), AppError> {
    enforce_origin(&state, &headers)?;
    if let Some(token) = session_token(&headers) {
        state.auth.logout(token).await?;
    }
    let mut response_headers = HeaderMap::new();
    response_headers.insert(
        SET_COOKIE,
        HeaderValue::from_str(&expired_cookie(&state)).map_err(|_| AppError::Internal)?,
    );
    Ok((StatusCode::NO_CONTENT, response_headers))
}

pub async fn me(
    State(state): State<AppState>,
    headers: HeaderMap,
) -> Result<Json<SessionIdentity>, AppError> {
    Ok(Json(require_identity(&state, &headers).await?))
}

pub async fn require_identity(
    state: &AppState,
    headers: &HeaderMap,
) -> Result<SessionIdentity, AppError> {
    let token = session_token(headers).ok_or(AppError::Unauthorized)?;
    state.auth.authenticate(token).await
}

pub async fn optional_identity(
    state: &AppState,
    headers: &HeaderMap,
) -> Result<Option<SessionIdentity>, AppError> {
    match session_token(headers) {
        Some(token) => state.auth.authenticate(token).await.map(Some),
        None => Ok(None),
    }
}

pub fn enforce_origin(state: &AppState, headers: &HeaderMap) -> Result<(), AppError> {
    let Some(origin) = headers.get(axum::http::header::ORIGIN) else {
        return Err(AppError::Forbidden(
            "an allowed Origin header is required".to_owned(),
        ));
    };
    let origin = origin
        .to_str()
        .map_err(|_| AppError::Validation("invalid Origin header".to_owned()))?;
    if state
        .settings
        .allowed_origins
        .iter()
        .any(|allowed| allowed == origin)
    {
        Ok(())
    } else {
        Err(AppError::Forbidden(
            "request origin is not allowed".to_owned(),
        ))
    }
}

pub(crate) fn session_token(headers: &HeaderMap) -> Option<&str> {
    headers
        .get(COOKIE)?
        .to_str()
        .ok()?
        .split(';')
        .map(str::trim)
        .find_map(|cookie| cookie.strip_prefix(&format!("{SESSION_COOKIE}=")))
        .filter(|token| !token.is_empty())
}

fn user_agent(headers: &HeaderMap) -> Option<&str> {
    headers
        .get(USER_AGENT)
        .and_then(|value| value.to_str().ok())
}

fn session_headers(state: &AppState, token: &str) -> Result<HeaderMap, AppError> {
    let mut headers = HeaderMap::new();
    headers.insert(
        SET_COOKIE,
        HeaderValue::from_str(&active_cookie(state, token)).map_err(|_| AppError::Internal)?,
    );
    Ok(headers)
}

fn active_cookie(state: &AppState, token: &str) -> String {
    let secure = if state.settings.secure_cookies {
        "; Secure"
    } else {
        ""
    };
    format!(
        "{SESSION_COOKIE}={token}; Path=/; HttpOnly; SameSite=Strict; Max-Age={}{}",
        state.settings.session_ttl.as_secs(),
        secure
    )
}

pub fn expired_cookie(state: &AppState) -> String {
    let secure = if state.settings.secure_cookies {
        "; Secure"
    } else {
        ""
    };
    format!("{SESSION_COOKIE}=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0{secure}")
}

fn request_rate_key(headers: &HeaderMap, action: &str, subject: &str) -> String {
    let forwarded = headers
        .get("x-forwarded-for")
        .and_then(|value| value.to_str().ok())
        .and_then(|value| value.split(',').next())
        .and_then(|value| value.trim().parse::<std::net::IpAddr>().ok())
        .map(|value| value.to_string())
        .unwrap_or_else(|| "unknown".to_owned());
    let subject = subject.trim().to_lowercase();
    format!("api:{action}:{forwarded}:{subject}")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn cookie_parser_ignores_unrelated_values() {
        let mut headers = HeaderMap::new();
        headers.insert(
            COOKIE,
            HeaderValue::from_static("theme=dark; chat_session=secret-token"),
        );
        assert_eq!(session_token(&headers), Some("secret-token"));
    }
}
