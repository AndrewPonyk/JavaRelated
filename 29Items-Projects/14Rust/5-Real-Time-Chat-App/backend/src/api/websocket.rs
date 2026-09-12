use std::time::Instant;

use axum::{
    extract::{
        State, WebSocketUpgrade,
        ws::{CloseFrame, Message, WebSocket},
    },
    http::HeaderMap,
    response::Response,
};
use chat_shared::{ClientCommand, ServerEvent};
use futures_util::{SinkExt, StreamExt, stream::SplitSink};
use tokio::sync::broadcast;
use uuid::Uuid;

use crate::{
    domain::SessionIdentity, error::AppError, services::RoomSubscription, state::AppState,
};

use super::{
    auth::{enforce_origin, require_identity, session_token},
    json::ApiPath,
};

pub async fn connect(
    State(state): State<AppState>,
    ApiPath(room_id): ApiPath<Uuid>,
    headers: HeaderMap,
    upgrade: WebSocketUpgrade,
) -> Result<Response, AppError> {
    enforce_origin(&state, &headers)?;
    let token = session_token(&headers)
        .ok_or(AppError::Unauthorized)?
        .to_owned();
    let identity = require_identity(&state, &headers).await?;
    state
        .rooms
        .require_member(room_id, identity.user.id)
        .await?;
    let subscription =
        state
            .chat
            .join(room_id, identity.user.id, identity.user.username.clone())?;

    Ok(upgrade.on_upgrade(move |socket| {
        run_connection(socket, state, room_id, identity, subscription, token)
    }))
}

async fn run_connection(
    socket: WebSocket,
    state: AppState,
    room_id: Uuid,
    identity: SessionIdentity,
    mut subscription: RoomSubscription,
    session_token: String,
) {
    let (mut sender, mut receiver) = socket.split();
    let mut heartbeat = tokio::time::interval(state.settings.heartbeat_interval);
    heartbeat.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Delay);
    let mut last_activity = Instant::now();

    if send_event(
        &mut sender,
        ServerEvent::OnlineCount {
            online_count: state.chat.online_count(room_id),
        },
    )
    .await
    .is_err()
    {
        return;
    }

    loop {
        tokio::select! {
            _ = state.shutdown.cancelled() => {
                let _ = sender.send(Message::Close(Some(CloseFrame {
                    code: 1012,
                    reason: "server restarting".into(),
                }))).await;
                break;
            }
            _ = heartbeat.tick() => {
                if state.auth.authenticate(&session_token).await.is_err() {
                    let _ = sender.send(Message::Close(Some(CloseFrame {
                        code: 1008,
                        reason: "session expired or revoked".into(),
                    }))).await;
                    break;
                }
                if last_activity.elapsed() > state.settings.heartbeat_timeout {
                    tracing::warn!(%room_id, user_id = %identity.user.id, "websocket heartbeat timed out");
                    break;
                }
                if sender.send(Message::Ping(Vec::new().into())).await.is_err() {
                    break;
                }
            }
            incoming = receiver.next() => {
                match incoming {
                    Some(Ok(message)) => {
                        last_activity = Instant::now();
                        if !handle_client_message(message, &mut sender, &state, room_id, &identity).await {
                            break;
                        }
                    }
                    Some(Err(error)) => {
                        tracing::debug!(%room_id, user_id = %identity.user.id, error = ?error, "websocket read failed");
                        break;
                    }
                    None => break,
                }
            }
            event = subscription.recv() => {
                match event {
                    Ok(event) => {
                        if send_event(&mut sender, event).await.is_err() {
                            break;
                        }
                    }
                    Err(broadcast::error::RecvError::Lagged(skipped)) => {
                        tracing::warn!(%room_id, user_id = %identity.user.id, skipped, "websocket receiver lagged");
                        let event = ServerEvent::error(
                            "resync_required",
                            "live messages were missed; reload message history",
                            true,
                        );
                        if send_event(&mut sender, event).await.is_err() {
                            break;
                        }
                    }
                    Err(broadcast::error::RecvError::Closed) => break,
                }
            }
        }
    }

    tracing::info!(%room_id, user_id = %identity.user.id, "websocket disconnected");
}

async fn handle_client_message(
    message: Message,
    sender: &mut SplitSink<WebSocket, Message>,
    state: &AppState,
    room_id: Uuid,
    identity: &SessionIdentity,
) -> bool {
    match message {
        Message::Text(text) => {
            if text.len() > state.settings.max_message_bytes.saturating_add(1024) {
                return send_public_error(
                    sender,
                    &AppError::Validation("WebSocket command is too large".to_owned()),
                )
                .await;
            }
            let command = match serde_json::from_str::<ClientCommand>(text.as_str()) {
                Ok(command) => command,
                Err(_) => {
                    return send_event(
                        sender,
                        ServerEvent::error("invalid_command", "invalid WebSocket command", false),
                    )
                    .await
                    .is_ok();
                }
            };
            if let ClientCommand::Ping { nonce } = &command {
                return send_event(sender, ServerEvent::Pong { nonce: *nonce })
                    .await
                    .is_ok();
            }
            let result = handle_command(state, room_id, identity, command).await;
            if let Err(error) = result {
                tracing::warn!(%room_id, user_id = %identity.user.id, error = ?error, "WebSocket command rejected");
                return send_public_error(sender, &error).await;
            }
            true
        }
        Message::Ping(payload) => sender.send(Message::Pong(payload)).await.is_ok(),
        Message::Pong(_) => true,
        Message::Close(_) => false,
        Message::Binary(_) => send_event(
            sender,
            ServerEvent::error(
                "unsupported_frame",
                "binary frames are not supported",
                false,
            ),
        )
        .await
        .is_ok(),
    }
}

async fn handle_command(
    state: &AppState,
    room_id: Uuid,
    identity: &SessionIdentity,
    command: ClientCommand,
) -> Result<(), AppError> {
    match command {
        ClientCommand::SendMessage {
            client_message_id,
            content,
        } => {
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
                    client_message_id,
                    &content,
                    state.settings.max_message_bytes,
                )
                .await?;
            if creation.created {
                state.chat.publish(
                    room_id,
                    ServerEvent::MessageCreated {
                        message: creation.message,
                    },
                );
            }
        }
        ClientCommand::EditMessage {
            message_id,
            content,
        } => {
            let message = state
                .rooms
                .update_message(
                    identity.user.id,
                    room_id,
                    message_id,
                    &content,
                    state.settings.max_message_bytes,
                )
                .await?;
            state
                .chat
                .publish(room_id, ServerEvent::MessageUpdated { message });
        }
        ClientCommand::DeleteMessage { message_id } => {
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
        }
        ClientCommand::Typing { is_typing } => {
            state.rate_limiter.check(
                format!("typing:{}", identity.user.id),
                state.settings.messages_per_minute.saturating_mul(2),
            )?;
            state.chat.publish(
                room_id,
                ServerEvent::Typing {
                    user_id: identity.user.id,
                    username: identity.user.username.clone(),
                    is_typing,
                },
            );
        }
        ClientCommand::Ping { nonce } => {
            tracing::debug!(%nonce, "ping was handled at the socket boundary");
        }
    }
    Ok(())
}

async fn send_public_error(sender: &mut SplitSink<WebSocket, Message>, error: &AppError) -> bool {
    send_event(
        sender,
        ServerEvent::error(
            error.public_code(),
            error.public_message(),
            matches!(error, AppError::RateLimited { .. } | AppError::Unavailable),
        ),
    )
    .await
    .is_ok()
}

async fn send_event(
    sender: &mut SplitSink<WebSocket, Message>,
    event: ServerEvent,
) -> Result<(), axum::Error> {
    let payload = serde_json::to_string(&event).map_err(axum::Error::new)?;
    sender.send(Message::Text(payload.into())).await
}
