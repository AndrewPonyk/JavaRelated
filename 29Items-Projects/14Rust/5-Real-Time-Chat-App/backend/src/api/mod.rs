mod auth;
mod health;
mod json;
mod rooms;
mod users;
mod websocket;

use axum::{
    Router,
    extract::DefaultBodyLimit,
    http::{
        HeaderName, HeaderValue,
        header::{CONTENT_SECURITY_POLICY, X_CONTENT_TYPE_OPTIONS},
    },
    routing::{get, post},
};
use tower_http::{
    catch_panic::CatchPanicLayer,
    compression::CompressionLayer,
    request_id::{MakeRequestUuid, PropagateRequestIdLayer, SetRequestIdLayer},
    services::ServeDir,
    set_header::SetResponseHeaderLayer,
    trace::TraceLayer,
};

use crate::state::AppState;

pub fn router(state: AppState) -> Router {
    let api = Router::new()
        .route("/auth/register", post(auth::register))
        .route("/auth/login", post(auth::login))
        .route("/auth/logout", post(auth::logout))
        .route("/auth/me", get(auth::me))
        .route(
            "/users/me",
            get(auth::me)
                .put(users::update_profile)
                .delete(users::delete_account),
        )
        .route("/users/me/password", post(users::change_password))
        .route("/rooms", get(rooms::list).post(rooms::create))
        .route(
            "/rooms/{room_id}",
            get(rooms::get).put(rooms::update).delete(rooms::delete),
        )
        .route("/rooms/{room_id}/join", post(rooms::join))
        .route(
            "/rooms/{room_id}/members",
            get(rooms::list_members).post(rooms::add_member),
        )
        .route(
            "/rooms/{room_id}/members/{user_id}",
            axum::routing::put(rooms::update_member).delete(rooms::remove_member),
        )
        .route("/rooms/{room_id}/audit", get(rooms::audit_events))
        .route(
            "/rooms/{room_id}/messages",
            get(rooms::history).post(rooms::create_message),
        )
        .route(
            "/rooms/{room_id}/messages/{message_id}",
            get(rooms::get_message)
                .put(rooms::update_message)
                .delete(rooms::delete_message),
        )
        .fallback(api_not_found)
        .method_not_allowed_fallback(method_not_allowed)
        .layer(SetResponseHeaderLayer::if_not_present(
            axum::http::header::CACHE_CONTROL,
            HeaderValue::from_static("no-store"),
        ))
        .layer(DefaultBodyLimit::max(16 * 1024));

    let request_id_header = HeaderName::from_static("x-request-id");

    Router::new()
        .route("/health/live", get(health::live))
        .route("/health/ready", get(health::ready))
        .route("/ws/{room_id}", get(websocket::connect))
        .nest("/api", api)
        .fallback_service(
            ServeDir::new(state.settings.static_dir.clone()).append_index_html_on_directories(true),
        )
        .layer(SetResponseHeaderLayer::if_not_present(
            CONTENT_SECURITY_POLICY,
            HeaderValue::from_static(
                "default-src 'self'; connect-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'",
            ),
        ))
        .layer(SetResponseHeaderLayer::if_not_present(
            X_CONTENT_TYPE_OPTIONS,
            HeaderValue::from_static("nosniff"),
        ))
        .layer(SetResponseHeaderLayer::if_not_present(
            HeaderName::from_static("permissions-policy"),
            HeaderValue::from_static("camera=(), microphone=(), geolocation=()"),
        ))
        .layer(SetResponseHeaderLayer::if_not_present(
            HeaderName::from_static("strict-transport-security"),
            HeaderValue::from_static("max-age=63072000"),
        ))
        .layer(CompressionLayer::new())
        .layer(SetResponseHeaderLayer::if_not_present(
            HeaderName::from_static("referrer-policy"),
            HeaderValue::from_static("no-referrer"),
        ))
        .layer(TraceLayer::new_for_http())
        .layer(PropagateRequestIdLayer::new(request_id_header.clone()))
        .layer(SetRequestIdLayer::new(request_id_header, MakeRequestUuid))
        .layer(CatchPanicLayer::new())
        .with_state(state)
}

async fn api_not_found() -> crate::error::AppError {
    crate::error::AppError::NotFound("endpoint")
}

async fn method_not_allowed() -> crate::error::AppError {
    crate::error::AppError::MethodNotAllowed
}
