use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

/// Commands accepted from an authenticated WebSocket client.
#[derive(Debug, Clone, Deserialize, Serialize, PartialEq, Eq)]
#[serde(tag = "type", rename_all = "snake_case", deny_unknown_fields)]
pub enum ClientCommand {
    SendMessage {
        client_message_id: Uuid,
        content: String,
    },
    EditMessage {
        message_id: Uuid,
        content: String,
    },
    DeleteMessage {
        message_id: Uuid,
    },
    Typing {
        is_typing: bool,
    },
    Ping {
        nonce: Uuid,
    },
}

/// Canonical durable message returned by PostgreSQL.
#[derive(Debug, Clone, Deserialize, Serialize, PartialEq, Eq)]
pub struct ChatMessage {
    pub id: Uuid,
    pub client_message_id: Uuid,
    pub room_id: Uuid,
    pub sender_user_id: Uuid,
    pub sender: String,
    pub content: String,
    pub sent_at: DateTime<Utc>,
    pub edited_at: Option<DateTime<Utc>>,
    pub deleted_at: Option<DateTime<Utc>>,
}

/// Events emitted by the server. The `type` discriminator is a versioned public contract.
#[derive(Debug, Clone, Deserialize, Serialize, PartialEq, Eq)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum ServerEvent {
    MessageCreated {
        message: ChatMessage,
    },
    MessageUpdated {
        message: ChatMessage,
    },
    MessageDeleted {
        room_id: Uuid,
        message_id: Uuid,
        deleted_at: DateTime<Utc>,
    },
    UserJoined {
        user_id: Uuid,
        username: String,
        online_count: usize,
    },
    UserLeft {
        user_id: Uuid,
        username: String,
        online_count: usize,
    },
    OnlineCount {
        online_count: usize,
    },
    Typing {
        user_id: Uuid,
        username: String,
        is_typing: bool,
    },
    Error {
        code: String,
        message: String,
        retryable: bool,
    },
    Pong {
        nonce: Uuid,
    },
}

impl ServerEvent {
    pub fn error(code: impl Into<String>, message: impl Into<String>, retryable: bool) -> Self {
        Self::Error {
            code: code.into(),
            message: message.into(),
            retryable,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn client_command_round_trips_with_stable_tag() {
        let id = Uuid::new_v4();
        let command = ClientCommand::SendMessage {
            client_message_id: id,
            content: "hello".to_owned(),
        };
        let json = serde_json::to_string(&command).expect("serialize command");
        assert!(json.contains(r#""type":"send_message""#));
        assert_eq!(
            serde_json::from_str::<ClientCommand>(&json).unwrap(),
            command
        );
    }

    #[test]
    fn privileged_unknown_fields_are_rejected() {
        let json = format!(
            r#"{{"type":"delete_message","message_id":"{}","admin":true}}"#,
            Uuid::new_v4()
        );
        assert!(serde_json::from_str::<ClientCommand>(&json).is_err());
    }

    #[test]
    fn error_event_exposes_retryability() {
        let event = ServerEvent::error("rate_limited", "slow down", true);
        let json = serde_json::to_value(event).unwrap();
        assert_eq!(json["retryable"], true);
    }
}
