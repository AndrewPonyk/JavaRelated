use actix_web::{web, HttpResponse, Error as ActixError, http::StatusCode};
use deadpool_diesel::postgres::Pool as DbPool;
use deadpool_redis::Pool as RedisPool;
use diesel::prelude::*;
use redis::AsyncCommands;

use crate::db::models::Asset;
use crate::db::schema::assets::dsl::*;

#[derive(thiserror::Error, Debug)]
pub enum EdgeError {
    #[error("Database error: {0}")]
    DbError(String),
    #[error("Redis error: {0}")]
    RedisError(String),
    #[error("Origin fetch error: {0}")]
    OriginError(String),
    #[error("Asset not found")]
    NotFound,
}

impl actix_web::error::ResponseError for EdgeError {
    fn status_code(&self) -> StatusCode {
        match self {
            EdgeError::NotFound => StatusCode::NOT_FOUND,
            EdgeError::OriginError(_) => StatusCode::BAD_GATEWAY,
            _ => StatusCode::INTERNAL_SERVER_ERROR,
        }
    }
}

pub async fn handle_edge_request(
    path: String,
    db_pool: web::Data<DbPool>,
    redis_pool: web::Data<RedisPool>,
) -> Result<HttpResponse, ActixError> {
    let mut redis_conn = redis_pool.get().await.map_err(|e| actix_web::error::ErrorInternalServerError(e))?;
    let cache_key = format!("cache:{}", path);

    // 1. Check Redis Cache
    let cached_data: Option<String> = redis_conn.get(&cache_key).await.map_err(|e| actix_web::error::ErrorInternalServerError(e))?;
    
    if let Some(data) = cached_data {
        tracing::info!("Cache HIT for {}", path);
        let _: () = redis_conn.incr("metrics:hits", 1).await.unwrap_or(());
        
        return Ok(HttpResponse::Ok()
            .body(data));
    }

    tracing::info!("Cache MISS for {}", path);
    let _: () = redis_conn.incr("metrics:misses", 1).await.unwrap_or(());

    // 2. Cache Miss -> Query Database for Origin
    let conn = db_pool.get().await.map_err(|e| actix_web::error::ErrorInternalServerError(e))?;
    
    let path_clone = path.clone();
    let asset_result = conn.interact(move |conn| {
        assets.filter(url_path.eq(path_clone)).first::<Asset>(conn)
    }).await.map_err(|e| actix_web::error::ErrorInternalServerError(e))?;

    let asset = match asset_result {
        Ok(a) => a,
        Err(diesel::result::Error::NotFound) => return Err(EdgeError::NotFound.into()),
        Err(e) => return Err(actix_web::error::ErrorInternalServerError(e)),
    };

    // 3. Fetch from Origin
    let client = reqwest::Client::new();
    let origin_resp = client.get(&asset.origin_url).send().await.map_err(|e| EdgeError::OriginError(e.to_string()))?;
    
    if !origin_resp.status().is_success() {
        return Err(EdgeError::OriginError(format!("Origin returned {}", origin_resp.status())).into());
    }

    let body = origin_resp.bytes().await.map_err(|e| EdgeError::OriginError(e.to_string()))?;

    // 4. Store in Redis
    let _: () = redis_conn.set_ex(&cache_key, body.to_vec(), asset.cache_ttl_seconds as u64)
        .await
        .map_err(|e| actix_web::error::ErrorInternalServerError(e))?;

    Ok(HttpResponse::Ok()
        .content_type(asset.content_type)
        .body(body))
}
