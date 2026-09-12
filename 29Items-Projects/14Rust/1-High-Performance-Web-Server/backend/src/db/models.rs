use chrono::NaiveDateTime;
use diesel::prelude::*;
use serde::{Deserialize, Serialize};

use crate::db::schema::assets;

#[derive(Queryable, Selectable, Insertable, Identifiable, AsChangeset, Serialize, Deserialize, Debug, Clone)]
#[diesel(table_name = assets)]
pub struct Asset {
    pub id: i32,
    pub url_path: String,
    pub origin_url: String,
    pub content_type: String,
    pub size_bytes: i64,
    pub cache_ttl_seconds: i32,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
}

#[derive(Insertable, Deserialize, Debug)]
#[diesel(table_name = assets)]
pub struct NewAsset {
    pub url_path: String,
    pub origin_url: String,
    pub content_type: String,
    pub size_bytes: i64,
    pub cache_ttl_seconds: i32,
}
