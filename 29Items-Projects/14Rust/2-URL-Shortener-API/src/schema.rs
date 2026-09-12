diesel::table! {
    urls (id) {
        id -> Integer,
        short_code -> Text,
        long_url -> Text,
        visit_count -> BigInt,
        status -> Text,
        created_at -> Timestamp,
        updated_at -> Timestamp,
        expires_at -> Nullable<Timestamp>,
    }
}
