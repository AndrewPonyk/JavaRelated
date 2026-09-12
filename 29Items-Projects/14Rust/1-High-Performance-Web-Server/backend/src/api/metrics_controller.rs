use actix_web::{get, web, HttpResponse, Responder};
use serde::{Deserialize, Serialize};
use deadpool_redis::Pool as RedisPool;
use redis::AsyncCommands;

#[derive(Serialize, Deserialize)]
pub struct EdgeMetrics {
    pub node_id: String,
    pub request_count: u64,
    pub cache_hit_rate: f64,
    pub latency_ms: u32,
    pub total_assets_cached: u64,
}

#[derive(Serialize)]
struct ErrorResponse {
    error: String,
}

#[get("/api/metrics")]
pub async fn get_metrics(redis_pool: web::Data<RedisPool>) -> impl Responder {
    let mut redis_conn = match redis_pool.get().await {
        Ok(c) => c,
        Err(e) => {
            tracing::error!("Redis pool error: {}", e);
            return HttpResponse::InternalServerError().json(ErrorResponse { error: "Failed to connect to Redis".to_string() });
        }
    };

    let hits: u64 = match redis_conn.get("metrics:hits").await {
        Ok(v) => v,
        Err(e) => {
            if e.kind() == redis::ErrorKind::TypeError { 0 } else {
                tracing::error!("Redis get hits error: {}", e);
                return HttpResponse::InternalServerError().json(ErrorResponse { error: "Failed to read metrics".to_string() });
            }
        }
    };
    
    let misses: u64 = match redis_conn.get("metrics:misses").await {
        Ok(v) => v,
        Err(e) => {
            if e.kind() == redis::ErrorKind::TypeError { 0 } else {
                tracing::error!("Redis get misses error: {}", e);
                return HttpResponse::InternalServerError().json(ErrorResponse { error: "Failed to read metrics".to_string() });
            }
        }
    };
    
    let total_reqs = hits + misses;
    
    let hit_rate = if total_reqs > 0 {
        (hits as f64 / total_reqs as f64) * 100.0
    } else {
        0.0
    };

    let metrics = vec![
        EdgeMetrics {
            node_id: "edge-local-1".to_string(),
            request_count: total_reqs,
            cache_hit_rate: hit_rate,
            latency_ms: 12, // Dummy latency
            total_assets_cached: hits, // Rough estimation
        }
    ];

    HttpResponse::Ok().json(metrics)
}

pub fn configure(cfg: &mut web::ServiceConfig) {
    cfg.service(get_metrics);
}
