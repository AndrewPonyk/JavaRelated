// @generated automatically by Diesel CLI.

diesel::table! {
    assets (id) {
        id -> Int4,
        #[max_length = 255]
        url_path -> Varchar,
        origin_url -> Text,
        #[max_length = 100]
        content_type -> Varchar,
        size_bytes -> Int8,
        cache_ttl_seconds -> Int4,
        created_at -> Timestamp,
        updated_at -> Timestamp,
    }
}
