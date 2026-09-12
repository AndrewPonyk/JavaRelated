use axum::{
    Json,
    http::{HeaderValue, StatusCode, header::RETRY_AFTER},
    response::{IntoResponse, Response},
};
use serde::Serialize;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum AppError {
    #[error("{0}")]
    Validation(String),
    #[error("request body is too large")]
    PayloadTooLarge,
    #[error("Content-Type must be application/json")]
    UnsupportedMediaType,
    #[error("authentication is required")]
    Unauthorized,
    #[error("{0}")]
    Forbidden(String),
    #[error("{0} was not found")]
    NotFound(&'static str),
    #[error("the HTTP method is not allowed for this endpoint")]
    MethodNotAllowed,
    #[error("{0}")]
    Conflict(String),
    #[error("rate limit exceeded")]
    RateLimited { retry_after_seconds: u64 },
    #[error("service is temporarily unavailable")]
    Unavailable,
    #[error("database operation failed")]
    Database(#[source] sqlx::Error),
    #[error("internal server error")]
    Internal,
}

#[derive(Debug, Serialize)]
struct ErrorEnvelope {
    error: ErrorDetails,
}

#[derive(Debug, Serialize)]
struct ErrorDetails {
    code: &'static str,
    message: String,
}

impl AppError {
    pub fn public_code(&self) -> &'static str {
        match self {
            Self::Validation(_) => "validation_error",
            Self::PayloadTooLarge => "payload_too_large",
            Self::UnsupportedMediaType => "unsupported_media_type",
            Self::Unauthorized => "unauthorized",
            Self::Forbidden(_) => "forbidden",
            Self::NotFound(_) => "not_found",
            Self::MethodNotAllowed => "method_not_allowed",
            Self::Conflict(_) => "conflict",
            Self::RateLimited { .. } => "rate_limited",
            Self::Unavailable => "unavailable",
            Self::Database(_) | Self::Internal => "internal_error",
        }
    }

    pub fn public_message(&self) -> String {
        match self {
            Self::Database(error) => {
                tracing::error!(error = ?error, "database operation failed");
                "the request could not be completed".to_owned()
            }
            Self::Internal => {
                tracing::error!("unexpected internal error");
                "the request could not be completed".to_owned()
            }
            _ => self.to_string(),
        }
    }

    fn status(&self) -> StatusCode {
        match self {
            Self::Validation(_) => StatusCode::BAD_REQUEST,
            Self::PayloadTooLarge => StatusCode::PAYLOAD_TOO_LARGE,
            Self::UnsupportedMediaType => StatusCode::UNSUPPORTED_MEDIA_TYPE,
            Self::Unauthorized => StatusCode::UNAUTHORIZED,
            Self::Forbidden(_) => StatusCode::FORBIDDEN,
            Self::NotFound(_) => StatusCode::NOT_FOUND,
            Self::MethodNotAllowed => StatusCode::METHOD_NOT_ALLOWED,
            Self::Conflict(_) => StatusCode::CONFLICT,
            Self::RateLimited { .. } => StatusCode::TOO_MANY_REQUESTS,
            Self::Unavailable => StatusCode::SERVICE_UNAVAILABLE,
            Self::Database(_) | Self::Internal => StatusCode::INTERNAL_SERVER_ERROR,
        }
    }
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let retry_after = match self {
            Self::RateLimited {
                retry_after_seconds,
            } => Some(retry_after_seconds),
            _ => None,
        };
        let mut response = (
            self.status(),
            Json(ErrorEnvelope {
                error: ErrorDetails {
                    code: self.public_code(),
                    message: self.public_message(),
                },
            }),
        )
            .into_response();

        if let Some(seconds) = retry_after
            && let Ok(value) = HeaderValue::from_str(&seconds.to_string())
        {
            response.headers_mut().insert(RETRY_AFTER, value);
        }
        response
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn expected_errors_have_stable_public_codes() {
        assert_eq!(AppError::Unauthorized.public_code(), "unauthorized");
        assert_eq!(
            AppError::Forbidden("no".to_owned()).public_code(),
            "forbidden"
        );
        assert_eq!(
            AppError::RateLimited {
                retry_after_seconds: 2
            }
            .status(),
            StatusCode::TOO_MANY_REQUESTS
        );
    }
}
