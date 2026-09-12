use actix_cors::Cors;
use actix_web::{
    middleware::{Compress, DefaultHeaders, Logger},
    web, App, HttpServer,
};
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};
use url_shortener_api::{
    api::{self, AppState, CreationRateLimiter},
    config::Settings,
    infrastructure::database::{create_pool, run_migrations},
    services::UrlService,
};

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    dotenvy::dotenv().ok();
    tracing_subscriber::registry()
        .with(
            tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| "info".into()),
        )
        .with(tracing_subscriber::fmt::layer())
        .init();
    let settings = Settings::from_env().map_err(std::io::Error::other)?;
    let pool = create_pool(&settings.database_url).map_err(std::io::Error::other)?;
    run_migrations(&pool).map_err(std::io::Error::other)?;
    let origins = settings.cors_allowed_origins.clone();
    let enable_hsts = settings.enable_hsts;
    let state = web::Data::new(AppState {
        service: UrlService::new(pool, settings.public_base_url),
        admin_api_token: settings.admin_api_token,
        creation_limiter: CreationRateLimiter::new(
            settings.creation_rate_window_seconds,
            settings.creation_rate_limit,
            settings.creation_rate_max_clients,
        ),
        trust_proxy_headers: settings.trust_proxy_headers,
    });
    tracing::info!(address = %settings.bind_address, "starting URL shortener API");
    HttpServer::new(move || {
        let mut cors = Cors::default()
            .allowed_methods(vec!["GET", "POST", "PUT", "DELETE"])
            .allowed_headers(vec![
                actix_web::http::header::CONTENT_TYPE,
                actix_web::http::header::HeaderName::from_static("x-admin-token"),
            ]);
        for origin in &origins {
            cors = cors.allowed_origin(origin);
        }
        let mut security_headers = DefaultHeaders::new()
            .add(("X-Content-Type-Options", "nosniff"))
            .add(("X-Frame-Options", "DENY"))
            .add(("Referrer-Policy", "no-referrer"))
            .add(("Cache-Control", "no-store"))
            .add((
                "Permissions-Policy",
                "camera=(), microphone=(), geolocation=()",
            ))
            .add((
                "Content-Security-Policy",
                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'",
            ));
        if enable_hsts {
            security_headers = security_headers.add((
                "Strict-Transport-Security",
                "max-age=31536000; includeSubDomains",
            ));
        }
        App::new()
            .app_data(state.clone())
            .wrap(Logger::default())
            .wrap(Compress::default())
            .wrap(security_headers)
            .wrap(cors)
            .configure(api::configure)
    })
    .bind(&settings.bind_address)?
    .run()
    .await
}
