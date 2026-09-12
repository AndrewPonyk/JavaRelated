use std::{env, path::Path};

use chat_backend::{
    domain::RoomRole,
    repositories::{ChatRepository, PostgresChatRepository},
};
use chrono::{Duration, Utc};
use sqlx::{PgPool, postgres::PgPoolOptions};
use uuid::Uuid;

struct TestDatabase {
    admin: PgPool,
    pool: PgPool,
    name: String,
}

impl TestDatabase {
    async fn create() -> Option<Self> {
        let admin_url = match env::var("TEST_DATABASE_URL") {
            Ok(url) => url,
            Err(_) => return None,
        };
        let admin = PgPoolOptions::new()
            .max_connections(2)
            .connect(&admin_url)
            .await
            .unwrap();
        let name = format!("chat_test_{}", Uuid::new_v4().simple());
        sqlx::query(&format!("CREATE DATABASE {name}"))
            .execute(&admin)
            .await
            .unwrap();
        let database_url = replace_database_name(&admin_url, &name);
        let pool = PgPoolOptions::new()
            .max_connections(5)
            .connect(&database_url)
            .await
            .unwrap();
        let migrations = Path::new(env!("CARGO_MANIFEST_DIR")).join("../migrations");
        sqlx::migrate::Migrator::new(migrations)
            .await
            .unwrap()
            .run(&pool)
            .await
            .unwrap();
        Some(Self { admin, pool, name })
    }

    async fn cleanup(self) {
        self.pool.close().await;
        sqlx::query(&format!("DROP DATABASE {} WITH (FORCE)", self.name))
            .execute(&self.admin)
            .await
            .unwrap();
        self.admin.close().await;
    }
}

fn replace_database_name(url: &str, database: &str) -> String {
    let (base, query) = url
        .split_once('?')
        .map_or((url, None), |(base, query)| (base, Some(query)));
    let prefix = base.rsplit_once('/').map_or(base, |(prefix, _)| prefix);
    match query {
        Some(query) => format!("{prefix}/{database}?{query}"),
        None => format!("{prefix}/{database}"),
    }
}

#[tokio::test]
async fn repository_supports_complete_account_room_membership_and_message_flow() {
    let Some(database) = TestDatabase::create().await else {
        return;
    };
    let repository = PostgresChatRepository::new(database.pool.clone());

    let owner = repository
        .create_user("Ada", "ada", "$argon2id$test-owner")
        .await
        .unwrap();
    let member = repository
        .create_user("Grace", "grace", "$argon2id$test-member")
        .await
        .unwrap();
    assert!(
        repository
            .find_user_by_normalized_username("ada")
            .await
            .unwrap()
            .is_some()
    );

    let session = repository
        .create_session(
            owner.id,
            "a".repeat(64).as_str(),
            Utc::now() + Duration::hours(1),
            None,
        )
        .await
        .unwrap();
    assert_eq!(session.user.id, owner.id);
    assert!(
        repository
            .find_session("a".repeat(64).as_str())
            .await
            .unwrap()
            .is_some()
    );

    let room = repository
        .create_room(
            owner.id,
            "Engineering",
            "engineering",
            "Build together",
            false,
        )
        .await
        .unwrap();
    assert_eq!(
        repository
            .get_membership(room.id, owner.id)
            .await
            .unwrap()
            .unwrap()
            .role,
        RoomRole::Owner
    );
    assert_eq!(
        repository.join_room(room.id, member.id).await.unwrap().role,
        RoomRole::Member
    );
    let changed = repository
        .update_member_role(room.id, member.id, RoomRole::Moderator)
        .await
        .unwrap()
        .unwrap();
    assert_eq!(changed.role, RoomRole::Moderator);

    let client_id = Uuid::new_v4();
    let first = repository
        .create_message(room.id, owner.id, "Ada", client_id, "Hello")
        .await
        .unwrap();
    let retried = repository
        .create_message(room.id, owner.id, "Ada", client_id, "Different content")
        .await
        .unwrap();
    assert!(first.created);
    assert!(!retried.created);
    assert_eq!(first.message.id, retried.message.id);
    assert_eq!(retried.message.content, "Hello");

    let history = repository.list_messages(room.id, None, 10).await.unwrap();
    assert_eq!(history.len(), 1);
    let edited = repository
        .update_message(first.message.id, "Edited")
        .await
        .unwrap()
        .unwrap();
    assert_eq!(edited.content, "Edited");
    let deleted = repository
        .delete_message(first.message.id)
        .await
        .unwrap()
        .unwrap();
    assert!(deleted.deleted_at.is_some());
    assert!(deleted.content.is_empty());

    let updated_room = repository
        .update_room(room.id, "Platform", "platform", "Updated", true)
        .await
        .unwrap()
        .unwrap();
    assert!(updated_room.is_private);
    assert!(
        repository
            .list_rooms(Some(member.id), 0, 100)
            .await
            .unwrap()
            .iter()
            .any(|view| view.room.id == room.id)
    );
    assert!(repository.delete_room(room.id).await.unwrap());
    assert!(repository.delete_user(owner.id).await.unwrap().is_some());
    assert!(repository.delete_user(member.id).await.unwrap().is_some());

    database.cleanup().await;
}
