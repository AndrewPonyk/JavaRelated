use std::{env, future::IntoFuture, path::Path, time::Duration};

use anyhow::Context;
use chat_backend::{
    build_app, config::Settings, repositories::PostgresChatRepository, state::AppState, telemetry,
};
use sqlx::{PgPool, postgres::PgPoolOptions};

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    dotenvy::dotenv().ok();
    let settings = Settings::from_env().context("invalid application configuration")?;
    telemetry::init(&settings.environment)?;

    let pool = PgPoolOptions::new()
        .max_connections(settings.database_max_connections)
        .acquire_timeout(Duration::from_secs(5))
        .connect(&settings.database_url)
        .await
        .context("failed to connect to PostgreSQL")?;

    run_migrations(&pool).await?;
    if env::args().any(|argument| argument == "--migrate-only") {
        tracing::info!("database migrations complete");
        return Ok(());
    }

    let repository = std::sync::Arc::new(PostgresChatRepository::new(pool.clone()));
    let state = AppState::new(settings.clone(), pool, repository);
    let shutdown = state.shutdown.clone();
    let app = build_app(state);
    let listener = tokio::net::TcpListener::bind(settings.bind_address())
        .await
        .context("failed to bind server listener")?;

    tracing::info!(address = %listener.local_addr()?, "chat server listening");
    let signal_shutdown = shutdown.clone();
    tokio::spawn(async move { shutdown_signal(signal_shutdown).await });
    let server_shutdown = shutdown.clone().cancelled_owned();
    let server = axum::serve(listener, app)
        .with_graceful_shutdown(server_shutdown)
        .into_future();
    tokio::pin!(server);
    let result = tokio::select! {
        result = &mut server => result,
        () = shutdown.cancelled() => {
            match tokio::time::timeout(settings.shutdown_timeout, &mut server).await {
                Ok(result) => result,
                Err(_) => {
                    tracing::warn!(
                        timeout_seconds = settings.shutdown_timeout.as_secs(),
                        "graceful shutdown deadline exceeded"
                    );
                    return Ok(());
                }
            }
        }
    };
    result.context("HTTP server failed")?;
    Ok(())
}

async fn run_migrations(pool: &PgPool) -> anyhow::Result<()> {
    let directory = env::var("MIGRATIONS_DIR").unwrap_or_else(|_| "migrations".to_owned());
    let migrator = sqlx::migrate::Migrator::new(Path::new(&directory))
        .await
        .with_context(|| format!("failed to load migrations from {directory}"))?;
    migrator
        .run(pool)
        .await
        .context("database migration failed")
}

async fn shutdown_signal(shutdown: tokio_util::sync::CancellationToken) {
    #[cfg(unix)]
    {
        use tokio::signal::unix::{SignalKind, signal};

        let mut terminate = match signal(SignalKind::terminate()) {
            Ok(signal) => signal,
            Err(error) => {
                tracing::error!(error = ?error, "failed to install SIGTERM handler");
                let _ = tokio::signal::ctrl_c().await;
                shutdown.cancel();
                return;
            }
        };
        tokio::select! {
            result = tokio::signal::ctrl_c() => {
                if let Err(error) = result {
                    tracing::error!(error = ?error, "failed to receive Ctrl+C");
                }
            }
            _ = terminate.recv() => {}
        }
    }
    #[cfg(not(unix))]
    if let Err(error) = tokio::signal::ctrl_c().await {
        tracing::error!(error = ?error, "failed to receive Ctrl+C");
    }
    tracing::info!("shutdown signal received");
    shutdown.cancel();
}
