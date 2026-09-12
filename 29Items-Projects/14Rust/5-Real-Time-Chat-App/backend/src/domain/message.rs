use chat_shared::ChatMessage;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Deserialize, Serialize, PartialEq, Eq)]
pub struct MessagePage {
    pub messages: Vec<ChatMessage>,
    pub next_cursor: Option<String>,
}

#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
pub struct MessageCreation {
    pub message: ChatMessage,
    pub created: bool,
}
