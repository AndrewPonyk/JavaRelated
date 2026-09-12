use actix_web::{get, post, delete, web, HttpResponse, Responder};
use deadpool_diesel::postgres::Pool as DbPool;
use diesel::prelude::*;
use crate::db::models::{Asset, NewAsset};
use crate::db::schema::assets::dsl::*;
use serde::Deserialize;

#[derive(Deserialize)]
pub struct CreateAssetReq {
    pub url_path: String,
    pub origin_url: String,
    pub content_type: String,
    pub size_bytes: i64,
    pub cache_ttl_seconds: i32,
}

impl CreateAssetReq {
    pub fn validate(&self) -> Result<(), &'static str> {
        if !self.url_path.starts_with('/') {
            return Err("url_path must start with a forward slash (/)");
        }
        if !self.origin_url.starts_with("http://") && !self.origin_url.starts_with("https://") {
            return Err("origin_url must be a valid HTTP or HTTPS URL");
        }
        Ok(())
    }
}

#[derive(serde::Serialize)]
struct ErrorResponse {
    error: String,
}

#[get("/api/assets")]
pub async fn get_assets(db_pool: web::Data<DbPool>) -> impl Responder {
    let conn = match db_pool.get().await {
        Ok(c) => c,
        Err(_) => return HttpResponse::InternalServerError().json(ErrorResponse { error: "Failed to acquire DB connection".to_string() }),
    };

    let results = conn.interact(|conn| {
        assets.load::<Asset>(conn)
    }).await;

    match results {
        Ok(Ok(data)) => HttpResponse::Ok().json(data),
        _ => HttpResponse::InternalServerError().json(ErrorResponse { error: "Failed to fetch assets".to_string() }),
    }
}

#[post("/api/assets")]
pub async fn create_asset(
    db_pool: web::Data<DbPool>,
    req: web::Json<CreateAssetReq>,
) -> impl Responder {
    if let Err(err_msg) = req.validate() {
        return HttpResponse::BadRequest().json(ErrorResponse { error: err_msg.to_string() });
    }

    let conn = match db_pool.get().await {
        Ok(c) => c,
        Err(_) => return HttpResponse::InternalServerError().json(ErrorResponse { error: "Failed to acquire DB connection".to_string() }),
    };

    let new_asset = NewAsset {
        url_path: req.url_path.clone(),
        origin_url: req.origin_url.clone(),
        content_type: req.content_type.clone(),
        size_bytes: req.size_bytes,
        cache_ttl_seconds: req.cache_ttl_seconds,
    };

    let result = conn.interact(move |conn| {
        diesel::insert_into(assets)
            .values(&new_asset)
            .get_result::<Asset>(conn)
    }).await;

    match result {
        Ok(Ok(data)) => HttpResponse::Created().json(data),
        _ => HttpResponse::InternalServerError().json(ErrorResponse { error: "Failed to create asset (maybe it already exists)".to_string() }),
    }
}

#[delete("/api/assets/{id}")]
pub async fn delete_asset(
    db_pool: web::Data<DbPool>,
    path: web::Path<i32>,
) -> impl Responder {
    let asset_id = path.into_inner();
    let conn = match db_pool.get().await {
        Ok(c) => c,
        Err(_) => return HttpResponse::InternalServerError().json(ErrorResponse { error: "Failed to acquire DB connection".to_string() }),
    };

    let result = conn.interact(move |conn| {
        diesel::delete(assets.filter(id.eq(asset_id))).execute(conn)
    }).await;

    match result {
        Ok(Ok(count)) if count > 0 => HttpResponse::Ok().json("Deleted"),
        Ok(Ok(_)) => HttpResponse::NotFound().json(ErrorResponse { error: "Asset not found".to_string() }),
        _ => HttpResponse::InternalServerError().json(ErrorResponse { error: "Failed to delete asset".to_string() }),
    }
}

pub fn configure(cfg: &mut web::ServiceConfig) {
    cfg.service(get_assets);
    cfg.service(create_asset);
    cfg.service(delete_asset);
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_valid_create_asset_req() {
        let req = CreateAssetReq {
            url_path: "/api/test".to_string(),
            origin_url: "https://example.com/api".to_string(),
            content_type: "application/json".to_string(),
            size_bytes: 100,
            cache_ttl_seconds: 3600,
        };
        assert!(req.validate().is_ok());
    }

    #[test]
    fn test_invalid_url_path() {
        let req = CreateAssetReq {
            url_path: "api/test".to_string(), // Missing leading slash
            origin_url: "https://example.com".to_string(),
            content_type: "application/json".to_string(),
            size_bytes: 100,
            cache_ttl_seconds: 3600,
        };
        assert_eq!(req.validate().unwrap_err(), "url_path must start with a forward slash (/)");
    }

    #[test]
    fn test_invalid_origin_url() {
        let req = CreateAssetReq {
            url_path: "/api/test".to_string(),
            origin_url: "ftp://example.com".to_string(), // Invalid scheme
            content_type: "application/json".to_string(),
            size_bytes: 100,
            cache_ttl_seconds: 3600,
        };
        assert_eq!(req.validate().unwrap_err(), "origin_url must be a valid HTTP or HTTPS URL");
    }
}
