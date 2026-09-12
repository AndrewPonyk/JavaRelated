use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    response::{IntoResponse, Response},
    routing::get,
    Json, Router,
};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

use crate::{
    models::compile_job::{CreateCompileJobRequest, UpdateCompileJobRequest},
    services::compile_service::ServiceError,
    AppState,
};

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/", get(list_compile_jobs).post(create_compile_job))
        .route(
            "/:id",
            get(get_compile_job)
                .put(update_compile_job)
                .delete(delete_compile_job),
        )
        .route("/:id/artifacts", get(list_artifacts))
        .route("/:id/artifacts/:artifact_id", get(get_artifact).delete(delete_artifact))
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ListParams {
    limit: Option<i64>,
    offset: Option<i64>,
}

async fn list_compile_jobs(
    State(state): State<AppState>,
    Query(params): Query<ListParams>,
) -> Response {
    let limit = params.limit.unwrap_or(50).clamp(1, 100);
    let offset = params.offset.unwrap_or(0).max(0);

    match state.compile_service.list(limit, offset).await {
        Ok(jobs) => Json(jobs).into_response(),
        Err(error) => ApiError::from_service(error).into_response(),
    }
}

async fn create_compile_job(
    State(state): State<AppState>,
    Json(payload): Json<CreateCompileJobRequest>,
) -> Response {
    match state.compile_service.create(payload).await {
        Ok(job) => (StatusCode::CREATED, Json(job)).into_response(),
        Err(error) => ApiError::from_service(error).into_response(),
    }
}

async fn get_compile_job(State(state): State<AppState>, Path(id): Path<Uuid>) -> Response {
    match state.compile_service.get(id).await {
        Ok(Some(job)) => Json(job).into_response(),
        Ok(None) => ApiError::not_found("compile job not found").into_response(),
        Err(error) => ApiError::from_service(error).into_response(),
    }
}

async fn update_compile_job(
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
    Json(payload): Json<UpdateCompileJobRequest>,
) -> Response {
    match state.compile_service.update(id, payload).await {
        Ok(job) => Json(job).into_response(),
        Err(error) => ApiError::from_service(error).into_response(),
    }
}

async fn delete_compile_job(State(state): State<AppState>, Path(id): Path<Uuid>) -> Response {
    match state.compile_service.delete(id).await {
        Ok(true) => StatusCode::NO_CONTENT.into_response(),
        Ok(false) => ApiError::not_found("compile job not found").into_response(),
        Err(error) => ApiError::from_service(error).into_response(),
    }
}

async fn list_artifacts(State(state): State<AppState>, Path(id): Path<Uuid>) -> Response {
    match state.compile_service.list_artifacts(id).await {
        Ok(artifacts) => Json(artifacts).into_response(),
        Err(error) => ApiError::from_service(error).into_response(),
    }
}

async fn get_artifact(
    State(state): State<AppState>,
    Path((id, artifact_id)): Path<(Uuid, Uuid)>,
) -> Response {
    match state.compile_service.get_artifact(id, artifact_id).await {
        Ok(Some(artifact)) => Json(artifact).into_response(),
        Ok(None) => ApiError::not_found("compile artifact not found").into_response(),
        Err(error) => ApiError::from_service(error).into_response(),
    }
}

async fn delete_artifact(
    State(state): State<AppState>,
    Path((id, artifact_id)): Path<(Uuid, Uuid)>,
) -> Response {
    match state.compile_service.delete_artifact(id, artifact_id).await {
        Ok(true) => StatusCode::NO_CONTENT.into_response(),
        Ok(false) => ApiError::not_found("compile artifact not found").into_response(),
        Err(error) => ApiError::from_service(error).into_response(),
    }
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct ApiError {
    error: String,
}

impl ApiError {
    fn not_found(message: impl Into<String>) -> (StatusCode, Json<Self>) {
        (
            StatusCode::NOT_FOUND,
            Json(Self {
                error: message.into(),
            }),
        )
    }

    fn from_service(error: ServiceError) -> (StatusCode, Json<Self>) {
        let status = match &error {
            ServiceError::EmptySource
            | ServiceError::SourceTooLarge { .. }
            | ServiceError::InvalidTarget => StatusCode::BAD_REQUEST,
            ServiceError::NotFound => StatusCode::NOT_FOUND,
            ServiceError::Database(_)
            | ServiceError::InvalidStatus(_)
            | ServiceError::InvalidArtifactType(_)
            | ServiceError::Serialization(_) => StatusCode::INTERNAL_SERVER_ERROR,
        };

        (
            status,
            Json(Self {
                error: error.to_string(),
            }),
        )
    }
}
