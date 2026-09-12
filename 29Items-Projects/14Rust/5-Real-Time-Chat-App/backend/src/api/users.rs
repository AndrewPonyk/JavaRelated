use axum::{
    Json,
    extract::State,
    http::{HeaderMap, HeaderValue, StatusCode, header::SET_COOKIE},
};
use serde::Deserialize;

use crate::{domain::User, error::AppError, state::AppState};

use super::auth::{enforce_origin, expired_cookie, require_identity};
use super::json::ApiJson;

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct UpdateProfileRequest {
    username: String,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ChangePasswordRequest {
    current_password: String,
    new_password: String,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct DeleteAccountRequest {
    password: String,
}

pub async fn update_profile(
    State(state): State<AppState>,
    headers: HeaderMap,
    ApiJson(payload): ApiJson<UpdateProfileRequest>,
) -> Result<Json<User>, AppError> {
    enforce_origin(&state, &headers)?;
    let identity = require_identity(&state, &headers).await?;
    state
        .auth
        .update_profile(identity.user.id, &payload.username)
        .await
        .map(Json)
}

pub async fn change_password(
    State(state): State<AppState>,
    headers: HeaderMap,
    ApiJson(payload): ApiJson<ChangePasswordRequest>,
) -> Result<(StatusCode, HeaderMap), AppError> {
    enforce_origin(&state, &headers)?;
    let identity = require_identity(&state, &headers).await?;
    state
        .auth
        .change_password(
            identity.user.id,
            &identity.user.username,
            &payload.current_password,
            &payload.new_password,
        )
        .await?;
    Ok((StatusCode::NO_CONTENT, clear_cookie_headers(&state)?))
}

pub async fn delete_account(
    State(state): State<AppState>,
    headers: HeaderMap,
    ApiJson(payload): ApiJson<DeleteAccountRequest>,
) -> Result<(StatusCode, HeaderMap), AppError> {
    enforce_origin(&state, &headers)?;
    let identity = require_identity(&state, &headers).await?;
    let deleted_room_ids = state
        .auth
        .delete_account(identity.user.id, &identity.user.username, &payload.password)
        .await?;
    for room_id in deleted_room_ids {
        state.chat.remove_room(room_id);
    }
    Ok((StatusCode::NO_CONTENT, clear_cookie_headers(&state)?))
}

fn clear_cookie_headers(state: &AppState) -> Result<HeaderMap, AppError> {
    let mut headers = HeaderMap::new();
    headers.insert(
        SET_COOKIE,
        HeaderValue::from_str(&expired_cookie(state)).map_err(|_| AppError::Internal)?,
    );
    Ok(headers)
}
