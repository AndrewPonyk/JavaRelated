use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Deserialize, Serialize, PartialEq, Eq)]
pub struct Room {
    pub id: Uuid,
    pub name: String,
    pub description: String,
    pub is_private: bool,
    pub owner_user_id: Option<Uuid>,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Copy, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum RoomRole {
    Member,
    Moderator,
    Owner,
}

impl RoomRole {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Member => "member",
            Self::Moderator => "moderator",
            Self::Owner => "owner",
        }
    }

    pub fn can_moderate(self) -> bool {
        matches!(self, Self::Moderator | Self::Owner)
    }
}

impl TryFrom<&str> for RoomRole {
    type Error = ();

    fn try_from(value: &str) -> Result<Self, Self::Error> {
        match value {
            "member" => Ok(Self::Member),
            "moderator" => Ok(Self::Moderator),
            "owner" => Ok(Self::Owner),
            _ => Err(()),
        }
    }
}

#[derive(Debug, Clone, Deserialize, Serialize, PartialEq, Eq)]
pub struct Membership {
    pub room_id: Uuid,
    pub user_id: Uuid,
    pub username: String,
    pub role: RoomRole,
    pub joined_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Deserialize, Serialize, PartialEq, Eq)]
pub struct RoomView {
    #[serde(flatten)]
    pub room: Room,
    pub member_count: i64,
    pub online_count: usize,
    pub current_user_role: Option<RoomRole>,
}
