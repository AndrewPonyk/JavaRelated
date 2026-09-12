use actix_web::{
    http::{header, StatusCode},
    test, web, App,
};
use tempfile::TempDir;
use url_shortener_api::{
    api::{self, AppState, CreationRateLimiter},
    infrastructure::database::{create_pool, run_migrations},
    services::UrlService,
};

fn state() -> (TempDir, web::Data<AppState>) {
    let directory = tempfile::tempdir().unwrap();
    let database = directory.path().join("test.sqlite");
    let pool = create_pool(database.to_str().unwrap()).unwrap();
    run_migrations(&pool).unwrap();
    (
        directory,
        web::Data::new(AppState {
            service: UrlService::new(pool, "http://short.test".into()),
            admin_api_token: "test-admin-token-that-is-long-enough".into(),
            creation_limiter: CreationRateLimiter::new(60, 30, 10_000),
            trust_proxy_headers: false,
        }),
    )
}
fn admin(request: test::TestRequest) -> test::TestRequest {
    request.insert_header(("X-Admin-Token", "test-admin-token-that-is-long-enough"))
}

#[actix_rt::test]
async fn complete_url_lifecycle_persists_visits() {
    let (_dir, state) = state();
    let app = test::init_service(App::new().app_data(state).configure(api::configure)).await;
    let create = admin(test::TestRequest::post().uri("/api/v1/urls").set_json(
        serde_json::json!({"long_url":"https://example.com/a", "custom_code":"example-a"}),
    ))
    .to_request();
    let created: serde_json::Value = test::call_and_read_body_json(&app, create).await;
    assert_eq!(created["short_url"], "http://short.test/example-a");
    let redirect = test::call_service(
        &app,
        test::TestRequest::get().uri("/example-a").to_request(),
    )
    .await;
    assert_eq!(redirect.status(), StatusCode::FOUND);
    assert_eq!(
        redirect.headers().get(header::LOCATION).unwrap(),
        "https://example.com/a"
    );
    let get = test::call_service(
        &app,
        admin(test::TestRequest::get().uri("/api/v1/urls/example-a")).to_request(),
    )
    .await;
    let body: serde_json::Value = test::read_body_json(get).await;
    assert_eq!(body["visit_count"], 1);
    let update = admin(test::TestRequest::put().uri("/api/v1/urls/example-a").set_json(serde_json::json!({"long_url":"https://example.org/new", "status":"disabled", "expires_at":null}))).to_request();
    assert_eq!(
        test::call_service(&app, update).await.status(),
        StatusCode::OK
    );
    assert_eq!(
        test::call_service(
            &app,
            test::TestRequest::get().uri("/example-a").to_request()
        )
        .await
        .status(),
        StatusCode::GONE
    );
    assert_eq!(
        test::call_service(
            &app,
            admin(test::TestRequest::delete().uri("/api/v1/urls/example-a")).to_request()
        )
        .await
        .status(),
        StatusCode::NO_CONTENT
    );
}

#[actix_rt::test]
async fn rejects_invalid_urls_and_protects_management_routes() {
    let (_dir, state) = state();
    let app = test::init_service(App::new().app_data(state).configure(api::configure)).await;
    let invalid = test::TestRequest::post()
        .uri("/api/v1/urls")
        .set_json(serde_json::json!({"long_url":"file:///not-allowed"}))
        .to_request();
    assert_eq!(
        test::call_service(&app, invalid).await.status(),
        StatusCode::BAD_REQUEST
    );
    assert_eq!(
        test::call_service(
            &app,
            test::TestRequest::get().uri("/api/v1/urls").to_request()
        )
        .await
        .status(),
        StatusCode::UNAUTHORIZED
    );
    assert_eq!(
        test::call_service(&app, test::TestRequest::get().uri("/missing").to_request())
            .await
            .status(),
        StatusCode::NOT_FOUND
    );
}

#[actix_rt::test]
async fn lists_urls_for_administrator() {
    let (_dir, state) = state();
    let app = test::init_service(App::new().app_data(state).configure(api::configure)).await;
    for url in ["https://example.com/one", "https://example.com/two"] {
        let request = test::TestRequest::post()
            .uri("/api/v1/urls")
            .set_json(serde_json::json!({"long_url":url}))
            .to_request();
        assert_eq!(
            test::call_service(&app, request).await.status(),
            StatusCode::CREATED
        );
    }
    let response: serde_json::Value = test::call_and_read_body_json(
        &app,
        admin(test::TestRequest::get().uri("/api/v1/urls?page=1&per_page=10")).to_request(),
    )
    .await;
    assert_eq!(response["items"].as_array().unwrap().len(), 2);
}
