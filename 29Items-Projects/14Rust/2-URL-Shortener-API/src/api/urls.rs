use crate::{api::AppState, domain::url::ShortUrl, error::AppError};
use actix_web::{http::header, web, HttpRequest, HttpResponse};
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use std::net::IpAddr;
use subtle::ConstantTimeEq;

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct CreateUrlRequest {
    pub long_url: String,
    pub custom_code: Option<String>,
    pub expires_at: Option<DateTime<Utc>>,
}
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct UpdateUrlRequest {
    pub long_url: String,
    pub status: String,
    pub expires_at: Option<DateTime<Utc>>,
}
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ListQuery {
    #[serde(default = "default_page")]
    pub page: i64,
    #[serde(default = "default_per_page")]
    pub per_page: i64,
}
fn default_page() -> i64 {
    1
}
fn default_per_page() -> i64 {
    20
}
#[derive(Serialize)]
pub struct UrlResponse {
    pub short_code: String,
    pub short_url: String,
    pub long_url: String,
    pub visit_count: i64,
    pub status: String,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
    pub expires_at: Option<DateTime<Utc>>,
}
#[derive(Serialize)]
pub struct ListResponse {
    pub page: i64,
    pub per_page: i64,
    pub items: Vec<UrlResponse>,
}
impl UrlResponse {
    fn from_url(url: ShortUrl, state: &AppState) -> Self {
        Self {
            short_url: state.service.short_url(&url.short_code),
            short_code: url.short_code,
            long_url: url.long_url,
            visit_count: url.visit_count,
            status: url.status,
            created_at: DateTime::from_naive_utc_and_offset(url.created_at, Utc),
            updated_at: DateTime::from_naive_utc_and_offset(url.updated_at, Utc),
            expires_at: url
                .expires_at
                .map(|value| DateTime::from_naive_utc_and_offset(value, Utc)),
        }
    }
}

pub async fn health(state: web::Data<AppState>) -> Result<HttpResponse, AppError> {
    let service = state.service.clone();
    web::block(move || service.health())
        .await
        .map_err(|_| AppError::Internal)??;
    Ok(HttpResponse::Ok().json(serde_json::json!({"status": "ok"})))
}
pub async fn create_url(
    state: web::Data<AppState>,
    request: HttpRequest,
    body: web::Json<CreateUrlRequest>,
) -> Result<HttpResponse, AppError> {
    state
        .creation_limiter
        .check(client_ip(&request, state.trust_proxy_headers))?;
    let body = body.into_inner();
    if body.custom_code.is_some() {
        require_admin(&request, &state)?;
    }
    let service = state.service.clone();
    let created =
        web::block(move || service.create(body.long_url, body.custom_code, body.expires_at))
            .await
            .map_err(|_| AppError::Internal)??;
    Ok(HttpResponse::Created().json(UrlResponse::from_url(created, &state)))
}
pub async fn list_urls(
    state: web::Data<AppState>,
    request: HttpRequest,
    query: web::Query<ListQuery>,
) -> Result<HttpResponse, AppError> {
    require_admin(&request, &state)?;
    let values = query.into_inner();
    let service = state.service.clone();
    let items = web::block(move || service.list(values.page, values.per_page))
        .await
        .map_err(|_| AppError::Internal)??;
    Ok(HttpResponse::Ok().json(ListResponse {
        page: values.page,
        per_page: values.per_page,
        items: items
            .into_iter()
            .map(|item| UrlResponse::from_url(item, &state))
            .collect(),
    }))
}
pub async fn get_url(
    state: web::Data<AppState>,
    request: HttpRequest,
    code: web::Path<String>,
) -> Result<HttpResponse, AppError> {
    require_admin(&request, &state)?;
    let service = state.service.clone();
    let url = web::block(move || service.get(&code))
        .await
        .map_err(|_| AppError::Internal)??;
    Ok(HttpResponse::Ok().json(UrlResponse::from_url(url, &state)))
}
pub async fn update_url(
    state: web::Data<AppState>,
    request: HttpRequest,
    code: web::Path<String>,
    body: web::Json<UpdateUrlRequest>,
) -> Result<HttpResponse, AppError> {
    require_admin(&request, &state)?;
    let update = body.into_inner();
    let service = state.service.clone();
    let url = web::block(move || {
        service.update(&code, update.long_url, update.status, update.expires_at)
    })
    .await
    .map_err(|_| AppError::Internal)??;
    Ok(HttpResponse::Ok().json(UrlResponse::from_url(url, &state)))
}
pub async fn delete_url(
    state: web::Data<AppState>,
    request: HttpRequest,
    code: web::Path<String>,
) -> Result<HttpResponse, AppError> {
    require_admin(&request, &state)?;
    let service = state.service.clone();
    web::block(move || service.delete(&code))
        .await
        .map_err(|_| AppError::Internal)??;
    Ok(HttpResponse::NoContent().finish())
}
pub async fn redirect(
    state: web::Data<AppState>,
    code: web::Path<String>,
) -> Result<HttpResponse, AppError> {
    let service = state.service.clone();
    let target = web::block(move || service.resolve(&code))
        .await
        .map_err(|_| AppError::Internal)??;
    Ok(HttpResponse::Found()
        .append_header((header::LOCATION, target.long_url))
        .finish())
}
pub async fn not_found() -> Result<HttpResponse, AppError> {
    Err(AppError::RouteNotFound)
}
pub async fn method_not_allowed() -> Result<HttpResponse, AppError> {
    Err(AppError::MethodNotAllowed)
}
fn client_ip(request: &HttpRequest, trust_proxy_headers: bool) -> Option<IpAddr> {
    if trust_proxy_headers {
        let forwarded = request
            .headers()
            .get("X-Forwarded-For")
            .and_then(|value| value.to_str().ok())
            .and_then(|value| value.split(',').next())
            .map(str::trim)
            .and_then(|value| value.parse().ok());
        if forwarded.is_some() {
            return forwarded;
        }
    }
    request.peer_addr().map(|address| address.ip())
}
fn require_admin(request: &HttpRequest, state: &AppState) -> Result<(), AppError> {
    let authorized = request
        .headers()
        .get("X-Admin-Token")
        .and_then(|value| value.to_str().ok())
        .map(|supplied| bool::from(supplied.as_bytes().ct_eq(state.admin_api_token.as_bytes())))
        .unwrap_or(false);
    if authorized {
        Ok(())
    } else {
        Err(AppError::Unauthorized)
    }
}
