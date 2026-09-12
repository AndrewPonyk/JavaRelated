use crate::schema::urls;
use chrono::NaiveDateTime;
use diesel::{Identifiable, Insertable, Queryable, Selectable};
use serde::Serialize;

#[derive(Debug, Clone, Queryable, Selectable, Identifiable, Serialize)]
#[diesel(table_name = urls)]
pub struct ShortUrl {
    pub id: i32,
    pub short_code: String,
    pub long_url: String,
    pub visit_count: i64,
    pub status: String,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub expires_at: Option<NaiveDateTime>,
}

#[derive(Insertable)]
#[diesel(table_name = urls)]
pub struct NewShortUrl<'a> {
    pub short_code: &'a str,
    pub long_url: &'a str,
    pub status: &'a str,
    pub expires_at: Option<NaiveDateTime>,
}

#[derive(Debug, Clone)]
pub struct UrlUpdate {
    pub long_url: String,
    pub status: String,
    pub expires_at: Option<NaiveDateTime>,
}
