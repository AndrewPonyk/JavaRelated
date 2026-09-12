pub mod api;
pub mod config;
pub mod domain;
pub mod error;
pub mod repositories;
pub mod services;
pub mod state;
pub mod telemetry;

use axum::Router;

use state::AppState;

pub fn build_app(state: AppState) -> Router {
    api::router(state)
}
