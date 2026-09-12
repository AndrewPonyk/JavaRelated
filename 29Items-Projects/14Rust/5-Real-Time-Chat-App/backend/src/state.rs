use std::sync::Arc;

use sqlx::PgPool;
use tokio_util::sync::CancellationToken;

use crate::{
    config::Settings,
    repositories::ChatRepository,
    services::{AuthService, ChatHub, RateLimiter, RoomService},
};

#[derive(Debug, Clone)]
pub struct AppState {
    pub settings: Arc<Settings>,
    pub pool: PgPool,
    pub auth: AuthService,
    pub rooms: RoomService,
    pub chat: ChatHub,
    pub rate_limiter: RateLimiter,
    pub shutdown: CancellationToken,
}

impl AppState {
    pub fn new(settings: Settings, pool: PgPool, repository: Arc<dyn ChatRepository>) -> Self {
        let chat = ChatHub::new(
            settings.chat_channel_capacity,
            settings.max_connections_per_room,
        );
        let auth = AuthService::new(
            repository.clone(),
            settings.session_ttl,
            settings.password_hash_concurrency,
        );
        let rooms = RoomService::new(repository);
        Self {
            settings: Arc::new(settings),
            pool,
            auth,
            rooms,
            chat,
            rate_limiter: RateLimiter::per_minute(),
            shutdown: CancellationToken::new(),
        }
    }
}
