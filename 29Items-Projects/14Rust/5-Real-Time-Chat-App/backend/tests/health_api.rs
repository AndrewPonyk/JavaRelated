use std::{
    net::{IpAddr, Ipv4Addr},
    path::PathBuf,
    sync::Arc,
    time::Duration,
};

use axum::{
    body::Body,
    http::{Method, Request, StatusCode},
};
use chat_backend::{
    build_app, config::Settings, repositories::PostgresChatRepository, state::AppState,
};
use http_body_util::BodyExt;
use sqlx::postgres::PgPoolOptions;
use tower::ServiceExt;

fn test_app() -> axum::Router {
    let settings = Settings {
        environment: "test".to_owned(),
        host: IpAddr::V4(Ipv4Addr::LOCALHOST),
        port: 0,
        database_url: "postgres://unused:unused@localhost/unused".to_owned(),
        database_max_connections: 1,
        static_dir: PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../frontend"),
        allowed_origins: vec!["http://localhost".to_owned()],
        chat_channel_capacity: 8,
        max_message_bytes: 4096,
        max_connections_per_room: 8,
        shutdown_timeout: Duration::from_secs(1),
        session_ttl: Duration::from_secs(3600),
        secure_cookies: false,
        max_list_page_size: 100,
        max_history_page_size: 100,
        password_hash_concurrency: 2,
        messages_per_minute: 60,
        api_requests_per_minute: 300,
        heartbeat_interval: Duration::from_secs(10),
        heartbeat_timeout: Duration::from_secs(30),
    };
    let pool = PgPoolOptions::new()
        .connect_lazy(&settings.database_url)
        .expect("valid test database URL");
    let repository = Arc::new(PostgresChatRepository::new(pool.clone()));
    build_app(AppState::new(settings, pool, repository))
}

#[tokio::test]
async fn liveness_does_not_depend_on_the_database() {
    let response = test_app()
        .oneshot(
            Request::builder()
                .uri("/health/live")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .expect("request completes");

    assert_eq!(response.status(), StatusCode::OK);
    assert_eq!(
        response.headers().get("x-content-type-options").unwrap(),
        "nosniff"
    );
    assert!(response.headers().contains_key("x-request-id"));
}

#[tokio::test]
async fn protected_endpoints_return_a_stable_json_error() {
    let response = test_app()
        .oneshot(
            Request::builder()
                .uri("/api/auth/me")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .expect("request completes");

    assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
    assert_eq!(
        response.headers().get("content-type").unwrap(),
        "application/json"
    );
}

#[tokio::test]
async fn static_frontend_index_is_served() {
    let response = test_app()
        .oneshot(Request::builder().uri("/").body(Body::empty()).unwrap())
        .await
        .expect("request completes");

    assert_eq!(response.status(), StatusCode::OK);
    assert_eq!(response.headers().get("content-type").unwrap(), "text/html");
}

#[tokio::test]
async fn malformed_json_uses_the_stable_error_envelope() {
    let response = test_app()
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/api/auth/register")
                .header("content-type", "application/json")
                .body(Body::from("{"))
                .unwrap(),
        )
        .await
        .expect("request completes");

    assert_eq!(response.status(), StatusCode::BAD_REQUEST);
    let body = response.into_body().collect().await.unwrap().to_bytes();
    let value: serde_json::Value = serde_json::from_slice(&body).unwrap();
    assert_eq!(value["error"]["code"], "validation_error");
}

async fn error_code(response: axum::response::Response) -> String {
    let body = response.into_body().collect().await.unwrap().to_bytes();
    let value: serde_json::Value = serde_json::from_slice(&body).unwrap();
    value["error"]["code"].as_str().unwrap().to_owned()
}

#[tokio::test]
async fn routing_and_extractor_failures_are_consistent_json() {
    for (method, uri, status, code) in [
        (
            Method::GET,
            "/api/rooms/not-a-uuid",
            StatusCode::BAD_REQUEST,
            "validation_error",
        ),
        (
            Method::GET,
            "/api/rooms?limit=invalid",
            StatusCode::BAD_REQUEST,
            "validation_error",
        ),
        (
            Method::GET,
            "/api/does-not-exist",
            StatusCode::NOT_FOUND,
            "not_found",
        ),
        (
            Method::PATCH,
            "/api/rooms",
            StatusCode::METHOD_NOT_ALLOWED,
            "method_not_allowed",
        ),
    ] {
        let response = test_app()
            .oneshot(
                Request::builder()
                    .method(method)
                    .uri(uri)
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .expect("request completes");
        assert_eq!(response.status(), status);
        assert_eq!(error_code(response).await, code);
    }
}

#[tokio::test]
async fn state_changes_require_origin_and_reject_unknown_fields() {
    let missing_origin = test_app()
        .oneshot(
            Request::builder()
                .method(Method::POST)
                .uri("/api/auth/register")
                .header("content-type", "application/json")
                .body(Body::from(
                    r#"{"username":"Ada","password":"correct horse 42"}"#,
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(missing_origin.status(), StatusCode::FORBIDDEN);

    let unknown_field = test_app()
        .oneshot(
            Request::builder()
                .method(Method::POST)
                .uri("/api/auth/register")
                .header("origin", "http://localhost")
                .header("content-type", "application/json")
                .body(Body::from(
                    r#"{"username":"Ada","password":"correct horse 42","admin":true}"#,
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(unknown_field.status(), StatusCode::BAD_REQUEST);

    let wrong_content_type = test_app()
        .oneshot(
            Request::builder()
                .method(Method::POST)
                .uri("/api/auth/register")
                .header("origin", "http://localhost")
                .body(Body::from(
                    r#"{"username":"Ada","password":"correct horse 42"}"#,
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(
        wrong_content_type.status(),
        StatusCode::UNSUPPORTED_MEDIA_TYPE
    );
    assert_eq!(
        error_code(wrong_content_type).await,
        "unsupported_media_type"
    );

    let oversized = test_app()
        .oneshot(
            Request::builder()
                .method(Method::POST)
                .uri("/api/auth/register")
                .header("origin", "http://localhost")
                .header("content-type", "application/json")
                .body(Body::from("x".repeat(17 * 1024)))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(oversized.status(), StatusCode::PAYLOAD_TOO_LARGE);
    assert_eq!(error_code(oversized).await, "payload_too_large");
}

#[tokio::test]
async fn static_assets_are_compressed_and_security_headers_are_present() {
    let response = test_app()
        .oneshot(
            Request::builder()
                .uri("/styles.css")
                .header("accept-encoding", "gzip")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::OK);
    assert_eq!(response.headers().get("content-encoding").unwrap(), "gzip");
    assert!(response.headers().contains_key("strict-transport-security"));
    assert!(response.headers().contains_key("permissions-policy"));
    let csp = response
        .headers()
        .get("content-security-policy")
        .unwrap()
        .to_str()
        .unwrap();
    assert!(csp.contains("connect-src 'self'"));
    assert!(!csp.contains("connect-src 'self' ws:"));

    let api_response = test_app()
        .oneshot(
            Request::builder()
                .uri("/api/auth/me")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(
        api_response.headers().get("cache-control").unwrap(),
        "no-store"
    );
}
