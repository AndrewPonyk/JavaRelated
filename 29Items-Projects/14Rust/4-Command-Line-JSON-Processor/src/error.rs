use thiserror::Error;

#[derive(Error, Debug)]
pub enum ProcessorError {
    #[error("I/O Error: {0}")]
    Io(#[from] std::io::Error),

    #[error("JSON Parsing Error: {0}")]
    Json(#[from] serde_json::Error),
}
