//! Types shared by the Rust server and protocol tooling.

pub mod protocol;

pub use protocol::{ChatMessage, ClientCommand, ServerEvent};
