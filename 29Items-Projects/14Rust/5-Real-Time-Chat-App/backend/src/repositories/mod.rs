mod postgres;

use async_trait::async_trait;
use chat_shared::ChatMessage;
use chrono::{DateTime, Utc};
use uuid::Uuid;

use crate::{
    domain::{
        AuditEvent, Membership, MessageCreation, Room, RoomRole, RoomView, SessionIdentity, User,
        UserCredentials,
    },
    error::AppError,
};

pub use postgres::PostgresChatRepository;

#[async_trait]
pub trait ChatRepository: Send + Sync {
    async fn create_user(
        &self,
        username: &str,
        normalized_username: &str,
        password_hash: &str,
    ) -> Result<User, AppError>;
    async fn find_user_by_normalized_username(
        &self,
        normalized_username: &str,
    ) -> Result<Option<UserCredentials>, AppError>;
    async fn get_user(&self, user_id: Uuid) -> Result<Option<User>, AppError>;
    async fn update_user(
        &self,
        user_id: Uuid,
        username: &str,
        normalized_username: &str,
    ) -> Result<Option<User>, AppError>;
    async fn update_password(&self, user_id: Uuid, password_hash: &str) -> Result<(), AppError>;
    async fn delete_user(&self, user_id: Uuid) -> Result<Option<Vec<Uuid>>, AppError>;

    async fn create_session(
        &self,
        user_id: Uuid,
        token_hash: &str,
        expires_at: DateTime<Utc>,
        user_agent: Option<&str>,
    ) -> Result<SessionIdentity, AppError>;
    async fn find_session(&self, token_hash: &str) -> Result<Option<SessionIdentity>, AppError>;
    async fn revoke_session(&self, token_hash: &str) -> Result<(), AppError>;
    async fn revoke_all_sessions(&self, user_id: Uuid) -> Result<(), AppError>;
    async fn delete_expired_sessions(&self) -> Result<u64, AppError>;

    async fn list_rooms(
        &self,
        current_user_id: Option<Uuid>,
        offset: i64,
        limit: i64,
    ) -> Result<Vec<RoomView>, AppError>;
    async fn get_room(&self, room_id: Uuid) -> Result<Option<Room>, AppError>;
    async fn get_room_view(
        &self,
        room_id: Uuid,
        current_user_id: Option<Uuid>,
    ) -> Result<Option<RoomView>, AppError>;
    async fn create_room(
        &self,
        owner_user_id: Uuid,
        name: &str,
        normalized_name: &str,
        description: &str,
        is_private: bool,
    ) -> Result<Room, AppError>;
    async fn update_room(
        &self,
        room_id: Uuid,
        name: &str,
        normalized_name: &str,
        description: &str,
        is_private: bool,
    ) -> Result<Option<Room>, AppError>;
    async fn delete_room(&self, room_id: Uuid) -> Result<bool, AppError>;

    async fn get_membership(
        &self,
        room_id: Uuid,
        user_id: Uuid,
    ) -> Result<Option<Membership>, AppError>;
    async fn list_members(
        &self,
        room_id: Uuid,
        offset: i64,
        limit: i64,
    ) -> Result<Vec<Membership>, AppError>;
    async fn add_member_by_username(
        &self,
        room_id: Uuid,
        normalized_username: &str,
        role: RoomRole,
    ) -> Result<Membership, AppError>;
    async fn join_room(&self, room_id: Uuid, user_id: Uuid) -> Result<Membership, AppError>;
    async fn update_member_role(
        &self,
        room_id: Uuid,
        user_id: Uuid,
        role: RoomRole,
    ) -> Result<Option<Membership>, AppError>;
    async fn remove_member(&self, room_id: Uuid, user_id: Uuid) -> Result<bool, AppError>;

    async fn list_messages(
        &self,
        room_id: Uuid,
        before: Option<(DateTime<Utc>, Uuid)>,
        limit: i64,
    ) -> Result<Vec<ChatMessage>, AppError>;
    async fn get_message(&self, message_id: Uuid) -> Result<Option<ChatMessage>, AppError>;
    async fn create_message(
        &self,
        room_id: Uuid,
        sender_user_id: Uuid,
        sender: &str,
        client_message_id: Uuid,
        content: &str,
    ) -> Result<MessageCreation, AppError>;
    async fn update_message(
        &self,
        message_id: Uuid,
        content: &str,
    ) -> Result<Option<ChatMessage>, AppError>;
    async fn delete_message(&self, message_id: Uuid) -> Result<Option<ChatMessage>, AppError>;

    async fn audit(
        &self,
        actor_user_id: Option<Uuid>,
        room_id: Option<Uuid>,
        action: &str,
        target_type: &str,
        target_id: Option<Uuid>,
    ) -> Result<(), AppError>;
    async fn list_audit_events(
        &self,
        room_id: Uuid,
        before_id: Option<i64>,
        limit: i64,
    ) -> Result<Vec<AuditEvent>, AppError>;
}
