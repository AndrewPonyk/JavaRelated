use actix_web::{
    http::{header, StatusCode},
    HttpResponse, ResponseError,
};
use serde::Serialize;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum AppError {
    #[error("invalid request: {0}")]
    Validation(String),
    #[error("authentication required")]
    Unauthorized,
    #[error("short URL not found")]
    NotFound,
    #[error("route not found")]
    RouteNotFound,
    #[error("method not allowed")]
    MethodNotAllowed,
    #[error("short code already exists")]
    Conflict,
    #[error("short URL is inactive")]
    Inactive,
    #[error("request rate limit exceeded; retry after {retry_after_seconds} seconds")]
    TooManyRequests { retry_after_seconds: u64 },
    #[error("internal server error")]
    Internal,
}

#[derive(Serialize)]
struct ErrorBody<'a> {
    code: &'a str,
    message: String,
}
impl ResponseError for AppError {
    fn status_code(&self) -> StatusCode {
        match self {
            Self::Validation(_) => StatusCode::BAD_REQUEST,
            Self::Unauthorized => StatusCode::UNAUTHORIZED,
            Self::NotFound | Self::RouteNotFound => StatusCode::NOT_FOUND,
            Self::MethodNotAllowed => StatusCode::METHOD_NOT_ALLOWED,
            Self::Conflict => StatusCode::CONFLICT,
            Self::Inactive => StatusCode::GONE,
            Self::TooManyRequests { .. } => StatusCode::TOO_MANY_REQUESTS,
            Self::Internal => StatusCode::INTERNAL_SERVER_ERROR,
        }
    }
    fn error_response(&self) -> HttpResponse {
        let code = match self {
            Self::Validation(_) => "validation_error",
            Self::Unauthorized => "unauthorized",
            Self::NotFound | Self::RouteNotFound => "not_found",
            Self::MethodNotAllowed => "method_not_allowed",
            Self::Conflict => "conflict",
            Self::Inactive => "inactive",
            Self::TooManyRequests { .. } => "rate_limit_exceeded",
            Self::Internal => "internal_error",
        };
        let mut response = HttpResponse::build(self.status_code());
        if let Self::TooManyRequests {
            retry_after_seconds,
        } = self
        {
            response.insert_header((header::RETRY_AFTER, retry_after_seconds.to_string()));
        }
        response.json(ErrorBody {
            code,
            message: self.to_string(),
        })
    }
}

impl From<diesel::result::Error> for AppError {
    fn from(error: diesel::result::Error) -> Self {
        match error {
            diesel::result::Error::NotFound => Self::NotFound,
            diesel::result::Error::DatabaseError(
                diesel::result::DatabaseErrorKind::UniqueViolation,
                _,
            ) => Self::Conflict,
            other => {
                tracing::error!(error = %other, "database operation failed");
                Self::Internal
            }
        }
    }
}
