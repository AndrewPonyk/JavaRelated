use clap::Parser;
use cli::Cli;
use processor::process_json_stream;
use std::path::PathBuf;

mod cli;
mod error;
mod processor;
mod schema;
mod utils;

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    // Initialize tracing (logging)
    tracing_subscriber::fmt::init();

    // Parse command line arguments
    let args = Cli::parse();

    let input_path = args.input.unwrap_or_else(|| PathBuf::from("-"));
    
    // Convert custom ProcessorError to anyhow error implicitly via ?
    process_json_stream(
        &input_path, 
        &args.filter, 
        args.schema, 
        args.pretty
    ).await?;

    Ok(())
}
