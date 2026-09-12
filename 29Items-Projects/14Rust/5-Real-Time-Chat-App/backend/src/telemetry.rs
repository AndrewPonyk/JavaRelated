use tracing_subscriber::{EnvFilter, layer::SubscriberExt, util::SubscriberInitExt};

pub fn init(environment: &str) -> anyhow::Result<()> {
    let filter = EnvFilter::try_from_default_env()
        .unwrap_or_else(|_| EnvFilter::new("chat_backend=info,tower_http=info"));
    let registry = tracing_subscriber::registry().with(filter);

    if environment == "production" {
        registry
            .with(tracing_subscriber::fmt::layer().json())
            .try_init()?;
    } else {
        registry
            .with(tracing_subscriber::fmt::layer().compact())
            .try_init()?;
    }
    Ok(())
}
