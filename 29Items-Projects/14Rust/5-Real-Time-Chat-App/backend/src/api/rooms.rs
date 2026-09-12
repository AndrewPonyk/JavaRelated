use axum::{
    Json,
    extract::State,
    http::{HeaderMap, StatusCode},
};
use chat_shared::{ChatMessage, ServerEvent};
use serde::Deserialize;
use uuid::Uuid;

use crate::{
    domain::{AuditEvent, Membership, MessagePage, Room, RoomRole, RoomView},
    error::AppError,
    state::AppState,
};

use super::auth::{enforce_origin, optional_identity, require_identity};
use super::json::{ApiJson, ApiPath, ApiQuery};

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct SaveRoomRequest {
    name: String,
    #[serde(default)]
    description: String,
    #[serde(default)]
    is_private: bool,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct AddMemberRequest {
    username: String,
    #[serde(default = "member_role")]
    role: RoomRole,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct UpdateMemberRequest {
    role: RoomRole,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct HistoryQuery {
    before: Option<String>,
    limit: Option<usize>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct AuditQuery {
    before_id: Option<i64>,
    limit: Option<usize>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct CreateMessageRequest {
    client_message_id: Uuid,
    content: String,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct UpdateMessageRequest {
    content: String,
}

fn member_role() -> RoomRole {
    RoomRole::Member
}

#[derive(Debug, Default, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ListQuery {
    #[serde(default)]
    offset: usize,
    limit: Option<usize>,
}

pub async fn list(
    State(state): State<AppState>,
    ApiQuery(query): ApiQuery<ListQuery>,
    headers: HeaderMap,
) -> Result<Json<Vec<RoomView>>, AppError> {
    let identity = optional_identity(&state, &headers).await?;
    let mut rooms = state
        .rooms
        .list(
            identity.as_ref().map(|value| value.user.id),
            query.offset,
            query.limit.unwrap_or(100),
            state.settings.max_list_page_size,
        )
        .await?;
    for room in &mut rooms {
        room.online_count = state.chat.online_count(room.room.id);
    }
    Ok(Json(rooms))
}

pub async fn get(
    State(state): State<AppState>,
    ApiPath(room_id): ApiPath<Uuid>,
    headers: HeaderMap,
) -> Result<Json<RoomView>, AppError> {
    let identity = optional_identity(&state, &headers).await?;
    let mut room = state
        .rooms
        .get(room_id, identity.as_ref().map(|value| value.user.id))
        .await?;
    room.online_count = state.chat.online_count(room_id);
    Ok(Json(room))
}

pub async fn create(
    State(state): State<AppState>,
    headers: HeaderMap,
    ApiJson(payload): ApiJson<SaveRoomRequest>,
) -> Result<(StatusCode, Json<Room>), AppError> {
    enforce_origin(&state, &headers)?;
    let identity = require_identity(&state, &headers).await?;
    state.rate_limiter.check(
        format!("rooms:create:{}", identity.user.id),
        state.settings.api_requests_per_minute.min(30),
    )?;
    let room = state
        .rooms
        .create(
            identity.user.id,
            &payload.name,
            &payload.description,
            payload.is_private,
        )
        .await?;
    Ok((StatusCode::CREATED, Json(room)))
}

pub async fn update(
    State(state): State<AppState>,
    ApiPath(room_id): ApiPath<Uuid>,
    headers: HeaderMap,
    ApiJson(payload): ApiJson<SaveRoomRequest>,
) -> Result<Json<Room>, AppError> {
    enforce_origin(&state, &headers)?;
    let identity = require_identity(&state, &headers).await?;
    state
        .rooms
        .update(
            identity.user.id,
            room_id,
            &payload.name,
            &payload.description,
            payload.is_private,
        )
        .await
        .map(Json)
}

pub async fn delete(
    State(state): State<AppState>,
    ApiPath(room_id): ApiPath<Uuid>,
    headers: HeaderMap,
) -> Result<StatusCode, AppError> {
    enforce_origin(&state, &headers)?;
    let identity = require_identity(&state, &headers).await?;
    state.rooms.delete(identity.user.id, room_id).await?;
    state.chat.remove_room(room_id);
    Ok(StatusCode::NO_CONTENT)
}

pub async fn join(
    State(state): State<AppState>,
    ApiPath(room_id): ApiPath<Uuid>,
    headers: HeaderMap,
) -> Result<Json<Membership>, AppError> {
    enforce_origin(&state, &headers)?;
    let identity = require_identity(&state, &headers).await?;
    state.rooms.join(room_id, identity.user.id).await.map(Json)
}

pub async fn list_members(
    State(state): State<AppState>,
    ApiPath(room_id): ApiPath<Uuid>,
    ApiQuery(query): ApiQuery<ListQuery>,
    headers: HeaderMap,
) -> Result<Json<Vec<Membership>>, AppError> {
    let identity = require_identity(&state, &headers).await?;
    state
        .rooms
        .list_members(
            identity.user.id,
            room_id,
            query.offset,
            query.limit.unwrap_or(100),
            state.settings.max_list_page_size,
        )
        .await
        .map(Json)
}

pub async fn add_member(
    State(state): State<AppState>,
    ApiPath(room_id): ApiPath<Uuid>,
    headers: HeaderMap,
    ApiJson(payload): ApiJson<AddMemberRequest>,
) -> Result<(StatusCode, Json<Membership>), AppError> {
    enforce_origin(&state, &headers)?;
    let identity = require_identity(&state, &headers).await?;
    let membership = state
        .rooms
        .add_member(identity.user.id, room_id, &payload.username, payload.role)
        .await?;
    Ok((StatusCode::CREATED, Json(membership)))
}

pub async fn update_member(
    State(state): State<AppState>,
    ApiPath((room_id, user_id)): ApiPath<(Uuid, Uuid)>,
    headers: HeaderMap,
    ApiJson(payload): ApiJson<UpdateMemberRequest>,
) -> Result<Json<Membership>, AppError> {
    enforce_origin(&state, &headers)?;
    let identity = require_identity(&state, &headers).await?;
    state
        .rooms
        .update_member_role(identity.user.id, room_id, user_id, payload.role)
        .await
        .map(Json)
}

pub async fn remove_member(
    State(state): State<AppState>,
    ApiPath((room_id, user_id)): ApiPath<(Uuid, Uuid)>,
    headers: HeaderMap,
) -> Result<StatusCode, AppError> {
    enforce_origin(&state, &headers)?;
    let identity = require_identity(&state, &headers).await?;
    state
        .rooms
        .remove_member(identity.user.id, room_id, user_id)
        .await?;
    Ok(StatusCode::NO_CONTENT)
}

pub async fn audit_events(
    State(state): State<AppState>,
    ApiPath(room_id): ApiPath<Uuid>,
    ApiQuery(query): ApiQuery<AuditQuery>,
    headers: HeaderMap,
) -> Result<Json<Vec<AuditEvent>>, AppError> {
    let identity = require_identity(&state, &headers).await?;
    state
        .rooms
        .audit_events(
            identity.user.id,
            room_id,
            query.before_id,
            query.limit.unwrap_or(50),
        )
        .await
        .map(Json)
}

pub async fn history(
    State(state): State<AppState>,
    ApiPath(room_id): ApiPath<Uuid>,
    ApiQuery(query): ApiQuery<HistoryQuery>,
    headers: HeaderMap,
) -> Result<Json<MessagePage>, AppError> {
    let identity = require_identity(&state, &headers).await?;
    state
        .rooms
        .history(
            identity.user.id,
            room_id,
            query.before.as_deref(),
            query.limit,
            state.settings.max_history_page_size,
        )
        .await
        .map(Json)
}

pub async fn get_message(
    State(state): State<AppState>,
    ApiPath((room_id, message_id)): ApiPath<(Uuid, Uuid)>,
    headers: HeaderMap,
) -> Result<Json<ChatMessage>, AppError> {
    let identity = require_identity(&state, &headers).await?;
    state
        .rooms
        .get_message(identity.user.id, room_id, message_id)
        .await
        .map(Json)
}

pub async fn create_message(
    State(state): State<AppState>,
    ApiPath(room_id): ApiPath<Uuid>,
    headers: HeaderMap,
    ApiJson(payload): ApiJson<CreateMessageRequest>,
) -> Result<(StatusCode, Json<ChatMessage>), AppError> {
    enforce_origin(&state, &headers)?;
    let identity = require_identity(&state, &headers).await?;
    state.rate_limiter.check(
        format!("messages:{}", identity.user.id),
        state.settings.messages_per_minute,
    )?;
    let creation = state
        .rooms
        .create_message(
            room_id,
            identity.user.id,
            &identity.user.username,
            payload.client_message_id,
            &payload.content,
            state.settings.max_message_bytes,
        )
        .await?;
    if creation.created {
        state.chat.publish(
            room_id,
            ServerEvent::MessageCreated {
                message: creation.message.clone(),
            },
        );
    }
    let status = if creation.created {
        StatusCode::CREATED
    } else {
        StatusCode::OK
    };
    Ok((status, Json(creation.message)))
}

pub async fn update_message(
    State(state): State<AppState>,
    ApiPath((room_id, message_id)): ApiPath<(Uuid, Uuid)>,
    headers: HeaderMap,
    ApiJson(payload): ApiJson<UpdateMessageRequest>,
) -> Result<Json<ChatMessage>, AppError> {
    enforce_origin(&state, &headers)?;
    let identity = require_identity(&state, &headers).await?;
    let message = state
        .rooms
        .update_message(
            identity.user.id,
            room_id,
            message_id,
            &payload.content,
            state.settings.max_message_bytes,
        )
        .await?;
    state.chat.publish(
        room_id,
        ServerEvent::MessageUpdated {
            message: message.clone(),
        },
    );
    Ok(Json(message))
}

pub async fn delete_message(
    State(state): State<AppState>,
    ApiPath((room_id, message_id)): ApiPath<(Uuid, Uuid)>,
    headers: HeaderMap,
) -> Result<StatusCode, AppError> {
    enforce_origin(&state, &headers)?;
    let identity = require_identity(&state, &headers).await?;
    let message = state
        .rooms
        .delete_message(identity.user.id, room_id, message_id)
        .await?;
    state.chat.publish(
        room_id,
        ServerEvent::MessageDeleted {
            room_id,
            message_id,
            deleted_at: message.deleted_at.ok_or(AppError::Internal)?,
        },
    );
    Ok(StatusCode::NO_CONTENT)
}
