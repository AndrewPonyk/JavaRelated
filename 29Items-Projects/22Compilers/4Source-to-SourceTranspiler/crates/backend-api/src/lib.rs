pub mod models;
pub mod routes;
pub mod services;

use axum::{extract::DefaultBodyLimit, routing::get, Json, Router};
use axum::http::{header, HeaderValue, Method};
use serde::Serialize;
use tower_http::{
    compression::CompressionLayer,
    cors::{AllowOrigin, Any, CorsLayer},
    set_header::SetResponseHeaderLayer,
    trace::TraceLayer,
};

use crate::services::compile_service::CompileService;

#[derive(Clone)]
pub struct AppState {
    pub compile_service: CompileService,
}

pub fn build_app(state: AppState) -> Router {
    Router::new()
        .route("/health", get(health))
        .nest("/api/compile-jobs", routes::compile_jobs::router())
        .with_state(state)
        .layer(DefaultBodyLimit::max(max_request_bytes()))
        .layer(SetResponseHeaderLayer::if_not_present(
            header::X_CONTENT_TYPE_OPTIONS,
            HeaderValue::from_static("nosniff"),
        ))
        .layer(SetResponseHeaderLayer::if_not_present(
            header::REFERRER_POLICY,
            HeaderValue::from_static("no-referrer"),
        ))
        .layer(CompressionLayer::new())
        .layer(cors_layer())
        .layer(TraceLayer::new_for_http())
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct HealthResponse {
    status: &'static str,
    service: &'static str,
}

async fn health() -> Json<HealthResponse> {
    Json(HealthResponse {
        status: "ok",
        service: "backend-api",
    })
}

fn cors_layer() -> CorsLayer {
    let allowed_origins = std::env::var("CORS_ALLOWED_ORIGINS")
        .unwrap_or_else(|_| "http://localhost:5173".to_string());
    let methods = [
        Method::GET,
        Method::POST,
        Method::PUT,
        Method::DELETE,
        Method::OPTIONS,
    ];

    if allowed_origins.trim() == "*" {
        return CorsLayer::new()
            .allow_origin(Any)
            .allow_methods(methods)
            .allow_headers([header::CONTENT_TYPE]);
    }

    let origins = allowed_origins
        .split(',')
        .filter_map(|origin| origin.trim().parse::<HeaderValue>().ok())
        .collect::<Vec<_>>();

    CorsLayer::new()
        .allow_origin(AllowOrigin::list(origins))
        .allow_methods(methods)
        .allow_headers([header::CONTENT_TYPE])
}

fn max_request_bytes() -> usize {
    std::env::var("MAX_SOURCE_BYTES")
        .ok()
        .and_then(|value| value.parse::<usize>().ok())
        .unwrap_or(1024 * 1024)
        .saturating_add(8192)
}
