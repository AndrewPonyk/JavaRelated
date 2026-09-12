use crate::{error::AppError, services::UrlService};
use actix_web::web;
use std::{
    collections::HashMap,
    net::{IpAddr, Ipv4Addr},
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};
mod urls;

#[derive(Clone)]
pub struct CreationRateLimiter {
    state: Arc<Mutex<RateLimitState>>,
    window: Duration,
    cleanup_interval: Duration,
    limit: u32,
    max_clients: usize,
}

struct RateLimitState {
    entries: HashMap<IpAddr, (Instant, u32)>,
    last_cleanup: Instant,
    oldest_started: Option<Instant>,
}

impl CreationRateLimiter {
    pub fn new(window_seconds: u64, limit: u32, max_clients: usize) -> Self {
        let window = Duration::from_secs(window_seconds);
        Self {
            state: Arc::new(Mutex::new(RateLimitState {
                entries: HashMap::new(),
                last_cleanup: Instant::now(),
                oldest_started: None,
            })),
            window,
            cleanup_interval: window.min(Duration::from_secs(60)),
            limit,
            max_clients,
        }
    }
    pub fn check(&self, address: Option<IpAddr>) -> Result<(), AppError> {
        let address = address.unwrap_or(IpAddr::V4(Ipv4Addr::UNSPECIFIED));
        let now = Instant::now();
        let mut state = self.state.lock().map_err(|error| {
            tracing::error!(error = %error, "creation rate limiter lock was poisoned");
            AppError::Internal
        })?;
        let oldest_entry_expired = state
            .oldest_started
            .is_some_and(|started| now.saturating_duration_since(started) >= self.window);
        if now.saturating_duration_since(state.last_cleanup) >= self.cleanup_interval
            || oldest_entry_expired
        {
            state
                .entries
                .retain(|_, (started, _)| now.saturating_duration_since(*started) < self.window);
            state.last_cleanup = now;
            state.oldest_started = state.entries.values().map(|(started, _)| *started).min();
        }
        if !state.entries.contains_key(&address) && state.entries.len() >= self.max_clients {
            let retry_after_seconds = state
                .oldest_started
                .map(|started| {
                    self.window
                        .saturating_sub(now.saturating_duration_since(started))
                })
                .unwrap_or(self.window)
                .as_secs()
                .max(1);
            return Err(AppError::TooManyRequests {
                retry_after_seconds,
            });
        }
        if state.oldest_started.is_none() {
            state.oldest_started = Some(now);
        }
        let entry = state.entries.entry(address).or_insert((now, 0));
        if entry.1 >= self.limit {
            let retry_after_seconds = self
                .window
                .saturating_sub(now.duration_since(entry.0))
                .as_secs()
                .max(1);
            return Err(AppError::TooManyRequests {
                retry_after_seconds,
            });
        }
        entry.1 += 1;
        Ok(())
    }
}
#[derive(Clone)]
pub struct AppState {
    pub service: UrlService,
    pub admin_api_token: String,
    pub creation_limiter: CreationRateLimiter,
    pub trust_proxy_headers: bool,
}
pub fn configure(cfg: &mut web::ServiceConfig) {
    cfg.app_data(
        web::JsonConfig::default()
            .limit(16_384)
            .error_handler(|error, _| {
                AppError::Validation(format!("invalid JSON body: {error}")).into()
            }),
    )
    .app_data(web::QueryConfig::default().error_handler(|error, _| {
        AppError::Validation(format!("invalid query parameters: {error}")).into()
    }))
    .app_data(web::PathConfig::default().error_handler(|error, _| {
        AppError::Validation(format!("invalid path parameters: {error}")).into()
    }))
    .service(
        web::resource("/health")
            .route(web::get().to(urls::health))
            .default_service(web::route().to(urls::method_not_allowed)),
    )
    .service(
        web::scope("/api/v1")
            .service(
                web::resource("/urls")
                    .route(web::post().to(urls::create_url))
                    .route(web::get().to(urls::list_urls))
                    .default_service(web::route().to(urls::method_not_allowed)),
            )
            .service(
                web::resource("/urls/{code}")
                    .route(web::get().to(urls::get_url))
                    .route(web::put().to(urls::update_url))
                    .route(web::delete().to(urls::delete_url))
                    .default_service(web::route().to(urls::method_not_allowed)),
            ),
    )
    .service(
        web::resource("/{code}")
            .route(web::get().to(urls::redirect))
            .default_service(web::route().to(urls::method_not_allowed)),
    )
    .default_service(web::route().to(urls::not_found));
}
