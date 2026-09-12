use actix_web::{http::StatusCode, test, web, App};
use tempfile::TempDir;
use url_shortener_api::{
    api::{self, AppState, CreationRateLimiter},
    error::AppError,
    infrastructure::database::{create_pool, run_migrations},
    services::UrlService,
};

const TOKEN: &str = "test-admin-token-that-is-at-least-32-characters";

fn state(limit: u32) -> (TempDir, web::Data<AppState>) {
    state_with_proxy(limit, false)
}

fn state_with_proxy(limit: u32, trust_proxy_headers: bool) -> (TempDir, web::Data<AppState>) {
    let directory = tempfile::tempdir().expect("temporary directory");
    let database = directory.path().join("security.sqlite");
    let pool = create_pool(database.to_str().expect("database path")).expect("database pool");
    run_migrations(&pool).expect("database migrations");
    (
        directory,
        web::Data::new(AppState {
            service: UrlService::new(pool, "http://short.test".into()),
            admin_api_token: TOKEN.into(),
            creation_limiter: CreationRateLimiter::new(60, limit, 10_000),
            trust_proxy_headers,
        }),
    )
}

fn admin(request: test::TestRequest) -> test::TestRequest {
    request.insert_header(("X-Admin-Token", TOKEN))
}

#[actix_rt::test]
async fn health_checks_database_and_errors_are_json() {
    let (_directory, state) = state(30);
    let app = test::init_service(App::new().app_data(state).configure(api::configure)).await;
    let health: serde_json::Value =
        test::call_and_read_body_json(&app, test::TestRequest::get().uri("/health").to_request())
            .await;
    assert_eq!(health["status"], "ok");

    let response = test::call_service(
        &app,
        test::TestRequest::post()
            .uri("/api/v1/urls")
            .set_json(serde_json::json!({"long_url": " ftp://example.com"}))
            .to_request(),
    )
    .await;
    assert_eq!(response.status(), StatusCode::BAD_REQUEST);
    let body: serde_json::Value = test::read_body_json(response).await;
    assert_eq!(body["code"], "validation_error");
}

#[actix_rt::test]
async fn malformed_payloads_queries_and_unknown_fields_use_json_errors() {
    let (_directory, state) = state(30);
    let app = test::init_service(App::new().app_data(state).configure(api::configure)).await;

    let requests = [
        test::TestRequest::post()
            .uri("/api/v1/urls")
            .insert_header(("Content-Type", "application/json"))
            .set_payload("{")
            .to_request(),
        test::TestRequest::post()
            .uri("/api/v1/urls")
            .set_json(serde_json::json!({
                "long_url": "https://example.com",
                "unexpected": true
            }))
            .to_request(),
        admin(test::TestRequest::get().uri("/api/v1/urls?per_page=many")).to_request(),
    ];

    for request in requests {
        let response = test::call_service(&app, request).await;
        assert_eq!(response.status(), StatusCode::BAD_REQUEST);
        assert_eq!(
            response
                .headers()
                .get("content-type")
                .and_then(|value| value.to_str().ok()),
            Some("application/json")
        );
        let body: serde_json::Value = test::read_body_json(response).await;
        assert_eq!(body["code"], "validation_error");
    }
}

#[actix_rt::test]
async fn custom_alias_requires_admin_and_rejects_conflicts_and_reserved_names() {
    let (_directory, state) = state(30);
    let app = test::init_service(App::new().app_data(state).configure(api::configure)).await;
    let body = serde_json::json!({"long_url": "https://example.com", "custom_code": "chosen"});

    let unauthorized = test::TestRequest::post()
        .uri("/api/v1/urls")
        .set_json(&body)
        .to_request();
    assert_eq!(
        test::call_service(&app, unauthorized).await.status(),
        StatusCode::UNAUTHORIZED
    );

    for expected in [StatusCode::CREATED, StatusCode::CONFLICT] {
        let request = admin(
            test::TestRequest::post()
                .uri("/api/v1/urls")
                .set_json(&body),
        )
        .to_request();
        assert_eq!(test::call_service(&app, request).await.status(), expected);
    }

    let reserved =
        admin(test::TestRequest::post().uri("/api/v1/urls").set_json(
            serde_json::json!({"long_url": "https://example.com", "custom_code": "health"}),
        ))
        .to_request();
    assert_eq!(
        test::call_service(&app, reserved).await.status(),
        StatusCode::BAD_REQUEST
    );
}

#[actix_rt::test]
async fn creation_rate_limit_and_pagination_boundaries_are_enforced() {
    let (_directory, state) = state(2);
    let app = test::init_service(App::new().app_data(state).configure(api::configure)).await;
    for expected in [
        StatusCode::CREATED,
        StatusCode::CREATED,
        StatusCode::TOO_MANY_REQUESTS,
    ] {
        let request = test::TestRequest::post()
            .uri("/api/v1/urls")
            .set_json(serde_json::json!({"long_url": "https://example.com"}))
            .to_request();
        assert_eq!(test::call_service(&app, request).await.status(), expected);
    }

    let invalid_page =
        admin(test::TestRequest::get().uri("/api/v1/urls?page=9223372036854775807&per_page=100"))
            .to_request();
    assert_eq!(
        test::call_service(&app, invalid_page).await.status(),
        StatusCode::BAD_REQUEST
    );

    for uri in [
        "/api/v1/urls?page=0",
        "/api/v1/urls?per_page=0",
        "/api/v1/urls?per_page=101",
    ] {
        let request = admin(test::TestRequest::get().uri(uri)).to_request();
        assert_eq!(
            test::call_service(&app, request).await.status(),
            StatusCode::BAD_REQUEST
        );
    }
}

#[actix_rt::test]
async fn update_and_delete_validate_input_and_missing_records() {
    let (_directory, state) = state(30);
    let app = test::init_service(App::new().app_data(state).configure(api::configure)).await;

    let invalid_status = admin(
        test::TestRequest::put()
            .uri("/api/v1/urls/missing")
            .set_json(serde_json::json!({
                "long_url": "https://example.com",
                "status": "archived",
                "expires_at": null
            })),
    )
    .to_request();
    assert_eq!(
        test::call_service(&app, invalid_status).await.status(),
        StatusCode::BAD_REQUEST
    );

    let past_expiry = admin(
        test::TestRequest::put()
            .uri("/api/v1/urls/missing")
            .set_json(serde_json::json!({
                "long_url": "https://example.com",
                "status": "active",
                "expires_at": "2000-01-01T00:00:00Z"
            })),
    )
    .to_request();
    assert_eq!(
        test::call_service(&app, past_expiry).await.status(),
        StatusCode::BAD_REQUEST
    );

    let valid_missing = admin(
        test::TestRequest::put()
            .uri("/api/v1/urls/missing")
            .set_json(serde_json::json!({
                "long_url": "https://example.com",
                "status": "active",
                "expires_at": null
            })),
    )
    .to_request();
    assert_eq!(
        test::call_service(&app, valid_missing).await.status(),
        StatusCode::NOT_FOUND
    );
    assert_eq!(
        test::call_service(
            &app,
            admin(test::TestRequest::delete().uri("/api/v1/urls/missing")).to_request()
        )
        .await
        .status(),
        StatusCode::NOT_FOUND
    );
}

#[actix_rt::test]
async fn concurrent_redirects_increment_without_lost_updates() {
    let directory = tempfile::tempdir().expect("temporary directory");
    let database = directory.path().join("concurrency.sqlite");
    let pool = create_pool(database.to_str().expect("database path")).expect("database pool");
    run_migrations(&pool).expect("database migrations");
    let service = UrlService::new(pool, "http://short.test".into());
    service
        .create(
            "https://example.com".into(),
            Some("concurrent".into()),
            None,
        )
        .expect("create URL");

    let workers = (0..20)
        .map(|_| {
            let service = service.clone();
            std::thread::spawn(move || service.resolve("concurrent"))
        })
        .collect::<Vec<_>>();
    for worker in workers {
        worker
            .join()
            .expect("redirect worker")
            .expect("successful redirect");
    }

    assert_eq!(
        service.get("concurrent").expect("stored URL").visit_count,
        20
    );
}

#[actix_rt::test]
async fn rate_limiter_rejects_calls_after_limit() {
    let limiter = CreationRateLimiter::new(60, 1, 10_000);
    assert!(limiter.check(None).is_ok());
    assert!(matches!(
        limiter.check(None),
        Err(AppError::TooManyRequests { .. })
    ));
}

#[actix_rt::test]
async fn rate_limiter_bounds_tracked_client_memory() {
    let limiter = CreationRateLimiter::new(60, 2, 1);
    let first = "198.51.100.10".parse().expect("valid IP address");
    let second = "198.51.100.11".parse().expect("valid IP address");

    assert!(limiter.check(Some(first)).is_ok());
    assert!(matches!(
        limiter.check(Some(second)),
        Err(AppError::TooManyRequests { .. })
    ));
    assert!(limiter.check(Some(first)).is_ok());
    assert!(matches!(
        limiter.check(Some(first)),
        Err(AppError::TooManyRequests { .. })
    ));
}

#[actix_rt::test]
async fn trusted_proxy_addresses_get_independent_limits_and_retry_hint() {
    let (_directory, state) = state_with_proxy(1, true);
    let app = test::init_service(App::new().app_data(state).configure(api::configure)).await;

    for address in ["198.51.100.10", "198.51.100.11"] {
        let request = test::TestRequest::post()
            .uri("/api/v1/urls")
            .insert_header(("X-Forwarded-For", address))
            .set_json(serde_json::json!({"long_url": "https://example.com"}))
            .to_request();
        assert_eq!(
            test::call_service(&app, request).await.status(),
            StatusCode::CREATED
        );
    }

    let limited = test::call_service(
        &app,
        test::TestRequest::post()
            .uri("/api/v1/urls")
            .insert_header(("X-Forwarded-For", "198.51.100.10"))
            .set_json(serde_json::json!({"long_url": "https://example.com"}))
            .to_request(),
    )
    .await;
    assert_eq!(limited.status(), StatusCode::TOO_MANY_REQUESTS);
    assert!(limited.headers().contains_key("Retry-After"));
}

#[actix_rt::test]
async fn unknown_routes_return_the_standard_json_error() {
    let (_directory, state) = state(30);
    let app = test::init_service(App::new().app_data(state).configure(api::configure)).await;
    let response = test::call_service(
        &app,
        test::TestRequest::get().uri("/not/a/route").to_request(),
    )
    .await;
    assert_eq!(response.status(), StatusCode::NOT_FOUND);
    let body: serde_json::Value = test::read_body_json(response).await;
    assert_eq!(body["code"], "not_found");

    let response =
        test::call_service(&app, test::TestRequest::post().uri("/health").to_request()).await;
    assert_eq!(response.status(), StatusCode::METHOD_NOT_ALLOWED);
    let body: serde_json::Value = test::read_body_json(response).await;
    assert_eq!(body["code"], "method_not_allowed");
}
