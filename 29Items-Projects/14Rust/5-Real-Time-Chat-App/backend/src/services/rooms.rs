use std::sync::Arc;

use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use chat_shared::ChatMessage;
use chrono::{DateTime, Utc};
use uuid::Uuid;

use crate::{
    domain::{AuditEvent, Membership, MessageCreation, MessagePage, Room, RoomRole, RoomView},
    error::AppError,
    repositories::ChatRepository,
};

#[derive(Clone)]
pub struct RoomService {
    repository: Arc<dyn ChatRepository>,
}

impl std::fmt::Debug for RoomService {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        formatter
            .debug_struct("RoomService")
            .finish_non_exhaustive()
    }
}

impl RoomService {
    pub fn new(repository: Arc<dyn ChatRepository>) -> Self {
        Self { repository }
    }

    pub async fn list(
        &self,
        current_user_id: Option<Uuid>,
        offset: usize,
        limit: usize,
        maximum_limit: usize,
    ) -> Result<Vec<RoomView>, AppError> {
        self.repository
            .list_rooms(
                current_user_id,
                offset.min(i64::MAX as usize) as i64,
                limit.clamp(1, maximum_limit) as i64,
            )
            .await
    }

    pub async fn get(
        &self,
        room_id: Uuid,
        current_user_id: Option<Uuid>,
    ) -> Result<RoomView, AppError> {
        self.repository
            .get_room_view(room_id, current_user_id)
            .await?
            .ok_or(AppError::NotFound("room"))
    }

    pub async fn create(
        &self,
        owner_user_id: Uuid,
        name: &str,
        description: &str,
        is_private: bool,
    ) -> Result<Room, AppError> {
        let name = normalize_required(name, "room name", 80)?;
        let description = normalize_optional(description, "description", 280)?;
        self.repository
            .create_room(
                owner_user_id,
                &name,
                &name.to_lowercase(),
                &description,
                is_private,
            )
            .await
    }

    pub async fn update(
        &self,
        actor_user_id: Uuid,
        room_id: Uuid,
        name: &str,
        description: &str,
        is_private: bool,
    ) -> Result<Room, AppError> {
        self.require_moderator(room_id, actor_user_id).await?;
        let name = normalize_required(name, "room name", 80)?;
        let description = normalize_optional(description, "description", 280)?;
        let room = self
            .repository
            .update_room(
                room_id,
                &name,
                &name.to_lowercase(),
                &description,
                is_private,
            )
            .await?
            .ok_or(AppError::NotFound("room"))?;
        self.repository
            .audit(
                Some(actor_user_id),
                Some(room_id),
                "room.updated",
                "room",
                Some(room_id),
            )
            .await?;
        Ok(room)
    }

    pub async fn delete(&self, actor_user_id: Uuid, room_id: Uuid) -> Result<(), AppError> {
        let membership = self.require_member(room_id, actor_user_id).await?;
        if membership.role != RoomRole::Owner {
            return Err(AppError::Forbidden(
                "only a room owner can delete the room".to_owned(),
            ));
        }
        self.repository
            .audit(
                Some(actor_user_id),
                Some(room_id),
                "room.deleted",
                "room",
                Some(room_id),
            )
            .await?;
        if !self.repository.delete_room(room_id).await? {
            return Err(AppError::NotFound("room"));
        }
        Ok(())
    }

    pub async fn join(&self, room_id: Uuid, user_id: Uuid) -> Result<Membership, AppError> {
        if let Some(existing) = self.repository.get_membership(room_id, user_id).await? {
            return Ok(existing);
        }
        self.repository
            .get_room(room_id)
            .await?
            .ok_or(AppError::NotFound("room"))?;
        let membership = self.repository.join_room(room_id, user_id).await?;
        self.repository
            .audit(
                Some(user_id),
                Some(room_id),
                "membership.joined",
                "user",
                Some(user_id),
            )
            .await?;
        Ok(membership)
    }

    pub async fn list_members(
        &self,
        actor_user_id: Uuid,
        room_id: Uuid,
        offset: usize,
        limit: usize,
        maximum_limit: usize,
    ) -> Result<Vec<Membership>, AppError> {
        self.require_member(room_id, actor_user_id).await?;
        self.repository
            .list_members(
                room_id,
                offset.min(i64::MAX as usize) as i64,
                limit.clamp(1, maximum_limit) as i64,
            )
            .await
    }

    pub async fn add_member(
        &self,
        actor_user_id: Uuid,
        room_id: Uuid,
        username: &str,
        role: RoomRole,
    ) -> Result<Membership, AppError> {
        let actor = self.require_moderator(room_id, actor_user_id).await?;
        if role == RoomRole::Owner || (role == RoomRole::Moderator && actor.role != RoomRole::Owner)
        {
            return Err(AppError::Forbidden(
                "only owners can grant elevated roles".to_owned(),
            ));
        }
        let username = normalize_required(username, "username", 40)?;
        let membership = self
            .repository
            .add_member_by_username(room_id, &username.to_lowercase(), role)
            .await?;
        self.repository
            .audit(
                Some(actor_user_id),
                Some(room_id),
                "membership.added",
                "user",
                Some(membership.user_id),
            )
            .await?;
        Ok(membership)
    }

    pub async fn update_member_role(
        &self,
        actor_user_id: Uuid,
        room_id: Uuid,
        user_id: Uuid,
        role: RoomRole,
    ) -> Result<Membership, AppError> {
        let actor = self.require_member(room_id, actor_user_id).await?;
        if actor.role != RoomRole::Owner {
            return Err(AppError::Forbidden(
                "only owners can change member roles".to_owned(),
            ));
        }
        let target = self
            .repository
            .get_membership(room_id, user_id)
            .await?
            .ok_or(AppError::NotFound("membership"))?;
        if role == RoomRole::Owner {
            return Err(AppError::Conflict(
                "ownership transfer is not supported by this endpoint".to_owned(),
            ));
        }
        if target.role == RoomRole::Owner {
            return Err(AppError::Conflict(
                "the room owner cannot be demoted".to_owned(),
            ));
        }
        if user_id == actor_user_id && role != RoomRole::Owner {
            return Err(AppError::Conflict(
                "an owner cannot demote their own membership".to_owned(),
            ));
        }
        let membership = self
            .repository
            .update_member_role(room_id, user_id, role)
            .await?
            .ok_or(AppError::NotFound("membership"))?;
        self.repository
            .audit(
                Some(actor_user_id),
                Some(room_id),
                "membership.role_updated",
                "user",
                Some(user_id),
            )
            .await?;
        Ok(membership)
    }

    pub async fn remove_member(
        &self,
        actor_user_id: Uuid,
        room_id: Uuid,
        user_id: Uuid,
    ) -> Result<(), AppError> {
        let actor = self.require_member(room_id, actor_user_id).await?;
        let target = self
            .repository
            .get_membership(room_id, user_id)
            .await?
            .ok_or(AppError::NotFound("membership"))?;
        if actor_user_id != user_id && !actor.role.can_moderate() {
            return Err(AppError::Forbidden(
                "moderator access is required to remove another member".to_owned(),
            ));
        }
        if target.role == RoomRole::Owner {
            return Err(AppError::Conflict(
                "room owners cannot be removed; transfer ownership or delete the room".to_owned(),
            ));
        }
        if target.role == RoomRole::Moderator && actor.role != RoomRole::Owner {
            return Err(AppError::Forbidden(
                "only a room owner can remove a moderator".to_owned(),
            ));
        }
        if !self.repository.remove_member(room_id, user_id).await? {
            return Err(AppError::NotFound("membership"));
        }
        self.repository
            .audit(
                Some(actor_user_id),
                Some(room_id),
                "membership.removed",
                "user",
                Some(user_id),
            )
            .await
    }

    pub async fn audit_events(
        &self,
        actor_user_id: Uuid,
        room_id: Uuid,
        before_id: Option<i64>,
        limit: usize,
    ) -> Result<Vec<AuditEvent>, AppError> {
        self.require_moderator(room_id, actor_user_id).await?;
        self.repository
            .list_audit_events(room_id, before_id, limit.clamp(1, 200) as i64)
            .await
    }

    pub async fn history(
        &self,
        user_id: Uuid,
        room_id: Uuid,
        cursor: Option<&str>,
        requested_limit: Option<usize>,
        maximum_limit: usize,
    ) -> Result<MessagePage, AppError> {
        self.require_member(room_id, user_id).await?;
        let limit = requested_limit.unwrap_or(50).clamp(1, maximum_limit);
        let before = cursor.map(decode_cursor).transpose()?;
        let mut messages = self
            .repository
            .list_messages(room_id, before, limit as i64)
            .await?;
        let next_cursor = (messages.len() == limit)
            .then(|| messages.last().map(encode_cursor))
            .flatten();
        messages.reverse();
        Ok(MessagePage {
            messages,
            next_cursor,
        })
    }

    pub async fn get_message(
        &self,
        user_id: Uuid,
        room_id: Uuid,
        message_id: Uuid,
    ) -> Result<ChatMessage, AppError> {
        self.require_member(room_id, user_id).await?;
        let message = self
            .repository
            .get_message(message_id)
            .await?
            .ok_or(AppError::NotFound("message"))?;
        if message.room_id != room_id {
            return Err(AppError::NotFound("message"));
        }
        Ok(message)
    }

    pub async fn create_message(
        &self,
        room_id: Uuid,
        user_id: Uuid,
        username: &str,
        client_message_id: Uuid,
        content: &str,
        max_message_bytes: usize,
    ) -> Result<MessageCreation, AppError> {
        self.require_member(room_id, user_id).await?;
        let content = validate_message(content, max_message_bytes)?;
        self.repository
            .create_message(room_id, user_id, username, client_message_id, &content)
            .await
    }

    pub async fn update_message(
        &self,
        actor_user_id: Uuid,
        room_id: Uuid,
        message_id: Uuid,
        content: &str,
        max_message_bytes: usize,
    ) -> Result<ChatMessage, AppError> {
        let actor = self.require_member(room_id, actor_user_id).await?;
        let existing = self.get_message(actor_user_id, room_id, message_id).await?;
        if existing.deleted_at.is_some() {
            return Err(AppError::Conflict(
                "deleted messages cannot be edited".to_owned(),
            ));
        }
        if existing.sender_user_id != actor_user_id && !actor.role.can_moderate() {
            return Err(AppError::Forbidden(
                "only the sender or a moderator can edit this message".to_owned(),
            ));
        }
        let content = validate_message(content, max_message_bytes)?;
        let message = self
            .repository
            .update_message(message_id, &content)
            .await?
            .ok_or(AppError::NotFound("message"))?;
        self.repository
            .audit(
                Some(actor_user_id),
                Some(room_id),
                "message.updated",
                "message",
                Some(message_id),
            )
            .await?;
        Ok(message)
    }

    pub async fn delete_message(
        &self,
        actor_user_id: Uuid,
        room_id: Uuid,
        message_id: Uuid,
    ) -> Result<ChatMessage, AppError> {
        let actor = self.require_member(room_id, actor_user_id).await?;
        let existing = self.get_message(actor_user_id, room_id, message_id).await?;
        if existing.sender_user_id != actor_user_id && !actor.role.can_moderate() {
            return Err(AppError::Forbidden(
                "only the sender or a moderator can delete this message".to_owned(),
            ));
        }
        let message = self
            .repository
            .delete_message(message_id)
            .await?
            .ok_or(AppError::NotFound("message"))?;
        self.repository
            .audit(
                Some(actor_user_id),
                Some(room_id),
                "message.deleted",
                "message",
                Some(message_id),
            )
            .await?;
        Ok(message)
    }

    pub async fn require_member(
        &self,
        room_id: Uuid,
        user_id: Uuid,
    ) -> Result<Membership, AppError> {
        self.repository
            .get_room(room_id)
            .await?
            .ok_or(AppError::NotFound("room"))?;
        self.repository
            .get_membership(room_id, user_id)
            .await?
            .ok_or_else(|| AppError::Forbidden("room membership is required".to_owned()))
    }

    async fn require_moderator(
        &self,
        room_id: Uuid,
        user_id: Uuid,
    ) -> Result<Membership, AppError> {
        let membership = self.require_member(room_id, user_id).await?;
        if !membership.role.can_moderate() {
            return Err(AppError::Forbidden(
                "room moderator access is required".to_owned(),
            ));
        }
        Ok(membership)
    }
}

fn normalize_required(value: &str, label: &str, max_chars: usize) -> Result<String, AppError> {
    let value = value.trim();
    if value.is_empty() {
        return Err(AppError::Validation(format!("{label} cannot be empty")));
    }
    validate_text(value, label, max_chars)?;
    Ok(value.to_owned())
}

fn normalize_optional(value: &str, label: &str, max_chars: usize) -> Result<String, AppError> {
    let value = value.trim();
    validate_text(value, label, max_chars)?;
    Ok(value.to_owned())
}

fn validate_text(value: &str, label: &str, max_chars: usize) -> Result<(), AppError> {
    if value.chars().count() > max_chars {
        return Err(AppError::Validation(format!(
            "{label} cannot exceed {max_chars} characters"
        )));
    }
    if value.chars().any(char::is_control) {
        return Err(AppError::Validation(format!(
            "{label} cannot contain control characters"
        )));
    }
    Ok(())
}

fn validate_message(content: &str, max_message_bytes: usize) -> Result<String, AppError> {
    let content = content.trim();
    if content.is_empty() {
        return Err(AppError::Validation("message cannot be empty".to_owned()));
    }
    if content.len() > max_message_bytes {
        return Err(AppError::Validation(format!(
            "message cannot exceed {max_message_bytes} UTF-8 bytes"
        )));
    }
    if content.chars().any(|character| character == '\0') {
        return Err(AppError::Validation(
            "message cannot contain null characters".to_owned(),
        ));
    }
    Ok(content.to_owned())
}

fn encode_cursor(message: &ChatMessage) -> String {
    URL_SAFE_NO_PAD.encode(format!("{}|{}", message.sent_at.to_rfc3339(), message.id))
}

fn decode_cursor(cursor: &str) -> Result<(DateTime<Utc>, Uuid), AppError> {
    let decoded = URL_SAFE_NO_PAD
        .decode(cursor)
        .map_err(|_| AppError::Validation("invalid message cursor".to_owned()))?;
    let decoded = String::from_utf8(decoded)
        .map_err(|_| AppError::Validation("invalid message cursor".to_owned()))?;
    let (timestamp, id) = decoded
        .split_once('|')
        .ok_or_else(|| AppError::Validation("invalid message cursor".to_owned()))?;
    let timestamp = DateTime::parse_from_rfc3339(timestamp)
        .map_err(|_| AppError::Validation("invalid message cursor".to_owned()))?
        .with_timezone(&Utc);
    let id = Uuid::parse_str(id)
        .map_err(|_| AppError::Validation("invalid message cursor".to_owned()))?;
    Ok((timestamp, id))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn text_validation_normalizes_boundaries() {
        assert_eq!(
            normalize_required("  General ", "room", 80).unwrap(),
            "General"
        );
        assert!(normalize_required("\n", "room", 80).is_err());
        assert!(validate_message(" ", 20).is_err());
        assert!(validate_message("four", 3).is_err());
    }

    #[test]
    fn history_cursor_round_trips() {
        let message = ChatMessage {
            id: Uuid::new_v4(),
            client_message_id: Uuid::new_v4(),
            room_id: Uuid::new_v4(),
            sender_user_id: Uuid::new_v4(),
            sender: "Ada".to_owned(),
            content: "hello".to_owned(),
            sent_at: Utc::now(),
            edited_at: None,
            deleted_at: None,
        };
        let decoded = decode_cursor(&encode_cursor(&message)).unwrap();
        assert_eq!(decoded.0, message.sent_at);
        assert_eq!(decoded.1, message.id);
    }
}
