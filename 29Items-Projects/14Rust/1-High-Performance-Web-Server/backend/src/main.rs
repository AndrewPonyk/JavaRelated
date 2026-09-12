use actix_web::{web, App, HttpServer, middleware::{Logger, Compress}, HttpRequest, HttpResponse};
use deadpool_diesel::postgres::{Manager as DbManager, Pool as DbPool};
use deadpool_redis::{Manager as RedisManager, Pool as RedisPool, Config as RedisConfig, Runtime};
use std::env;

mod api;
mod db;
mod services;

async fn wildcard_handler(
    req: HttpRequest,
    db_pool: web::Data<DbPool>,
    redis_pool: web::Data<RedisPool>,
) -> actix_web::Result<HttpResponse> {
    let path = req.path().to_string();
    services::edge_service::handle_edge_request(path, db_pool, redis_pool).await
}

async fn health_check() -> actix_web::Result<HttpResponse> {
    Ok(HttpResponse::Ok().json(serde_json::json!({ "status": "ok" })))
}

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    // Load .env variables
    dotenvy::dotenv().ok();
    
    // Initialize tracing
    tracing_subscriber::fmt::init();

    // 1. Setup Postgres Pool
    let db_url = env::var("DATABASE_URL").expect("DATABASE_URL must be set");
    let db_manager = DbManager::new(db_url, deadpool_diesel::Runtime::Tokio1);
    let db_pool = DbPool::builder(db_manager)
        .max_size(8)
        .build()
        .unwrap();

    // 2. Setup Redis Pool
    let redis_url = env::var("REDIS_URL").unwrap_or_else(|_| "redis://localhost:6379".to_string());
    let redis_cfg = RedisConfig::from_url(redis_url);
    let redis_pool = redis_cfg.create_pool(Some(Runtime::Tokio1)).unwrap();

    let port = env::var("PORT").unwrap_or_else(|_| "8080".to_string());
    tracing::info!("Starting Edge Server on port {}", port);

    HttpServer::new(move || {
        App::new()
            .wrap(Logger::default())
            .wrap(Compress::default())
            .app_data(web::Data::new(db_pool.clone()))
            .app_data(web::Data::new(redis_pool.clone()))
            // Health check
            .route("/health", web::get().to(health_check))
            // Control Plane API Routes
            .configure(api::metrics_controller::configure)
            .configure(api::assets_controller::configure)
            // Data Plane Edge Router (Catch-all)
            .default_service(web::route().to(wildcard_handler))
    })
    .bind(("0.0.0.0", port.parse().unwrap()))?
    .run()
    .await
}
