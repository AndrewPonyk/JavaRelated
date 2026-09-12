use std::{error::Error, io, net::SocketAddr};

use backend_api::{build_app, services::compile_service::CompileService, AppState};
use tokio::net::TcpListener;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt, EnvFilter};

#[tokio::main]
async fn main() -> Result<(), Box<dyn Error>> {
    tracing_subscriber::registry()
        .with(EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info")))
        .with(tracing_subscriber::fmt::layer())
        .init();

    let host = std::env::var("API_HOST").unwrap_or_else(|_| "0.0.0.0".to_string());
    let port = std::env::var("API_PORT")
        .ok()
        .and_then(|value| value.parse::<u16>().ok())
        .unwrap_or(8080);
    let addr: SocketAddr = format!("{host}:{port}")
        .parse()
        .map_err(|error| {
            io::Error::new(
                io::ErrorKind::InvalidInput,
                format!("API_HOST/API_PORT produced an invalid socket address: {error}"),
            )
        })?;

    let database_url = std::env::var("DATABASE_URL")
        .map_err(|_| io::Error::new(io::ErrorKind::InvalidInput, "DATABASE_URL must be set"))?;
    let state = AppState {
        compile_service: CompileService::connect(&database_url)
            .await
            .map_err(|error| io::Error::other(format!("failed to initialize compile service: {error}")))?,
    };
    let listener = TcpListener::bind(addr).await?;

    tracing::info!(%addr, "backend API listening");
    axum::serve(listener, build_app(state)).await?;
    Ok(())
}
