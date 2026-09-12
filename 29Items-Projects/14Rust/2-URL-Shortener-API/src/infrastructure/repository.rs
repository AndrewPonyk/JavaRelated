use crate::{
    domain::url::{NewShortUrl, ShortUrl, UrlUpdate},
    error::AppError,
    schema::urls,
};
use chrono::Utc;
use diesel::prelude::*;

pub struct UrlRepository;
impl UrlRepository {
    pub fn health(conn: &mut SqliteConnection) -> Result<(), AppError> {
        diesel::select(diesel::dsl::sql::<diesel::sql_types::Integer>("1"))
            .get_result::<i32>(conn)
            .map(|_| ())
            .map_err(AppError::from)
    }
    pub fn insert(
        conn: &mut SqliteConnection,
        value: NewShortUrl<'_>,
    ) -> Result<ShortUrl, AppError> {
        diesel::insert_into(urls::table)
            .values(&value)
            .execute(conn)
            .map_err(AppError::from)?;
        Self::find(conn, value.short_code)
    }
    pub fn find(conn: &mut SqliteConnection, code: &str) -> Result<ShortUrl, AppError> {
        urls::table
            .filter(urls::short_code.eq(code))
            .select(ShortUrl::as_select())
            .first(conn)
            .map_err(AppError::from)
    }
    pub fn list(
        conn: &mut SqliteConnection,
        offset: i64,
        limit: i64,
    ) -> Result<Vec<ShortUrl>, AppError> {
        urls::table
            .order((urls::created_at.desc(), urls::id.desc()))
            .offset(offset)
            .limit(limit)
            .select(ShortUrl::as_select())
            .load(conn)
            .map_err(AppError::from)
    }
    pub fn update(
        conn: &mut SqliteConnection,
        code: &str,
        update: UrlUpdate,
    ) -> Result<ShortUrl, AppError> {
        let changed = diesel::update(urls::table.filter(urls::short_code.eq(code)))
            .set((
                urls::long_url.eq(update.long_url),
                urls::status.eq(update.status),
                urls::expires_at.eq(update.expires_at),
                urls::updated_at.eq(Utc::now().naive_utc()),
            ))
            .execute(conn)
            .map_err(AppError::from)?;
        if changed == 0 {
            return Err(AppError::NotFound);
        }
        Self::find(conn, code)
    }
    pub fn delete(conn: &mut SqliteConnection, code: &str) -> Result<(), AppError> {
        if diesel::delete(urls::table.filter(urls::short_code.eq(code)))
            .execute(conn)
            .map_err(AppError::from)?
            == 0
        {
            return Err(AppError::NotFound);
        }
        Ok(())
    }
    pub fn resolve_and_increment(
        conn: &mut SqliteConnection,
        code: &str,
    ) -> Result<ShortUrl, AppError> {
        let now = Utc::now().naive_utc();
        let active_url = urls::table
            .filter(urls::short_code.eq(code))
            .filter(urls::status.eq("active"))
            .filter(urls::expires_at.is_null().or(urls::expires_at.gt(now)));
        let changed = diesel::update(active_url)
            .set((
                urls::visit_count.eq(urls::visit_count + 1),
                urls::updated_at.eq(now),
            ))
            .execute(conn)
            .map_err(AppError::from)?;
        if changed == 0 {
            return match Self::find(conn, code) {
                Ok(_) => Err(AppError::Inactive),
                Err(error) => Err(error),
            };
        }
        Self::find(conn, code)
    }
}
