use std::{
    env,
    net::{IpAddr, Ipv4Addr},
    path::{Path, PathBuf},
    sync::Arc,
    time::Duration,
};

use axum::{
    body::Body,
    http::{Method, Request, StatusCode},
};
use chat_backend::{
    build_app,
    config::Settings,
    domain::{
        AuditEvent, Membership, MessagePage, Room, RoomRole, RoomView, SessionIdentity, User,
    },
    repositories::PostgresChatRepository,
    state::AppState,
};
use chat_shared::ChatMessage;
use http_body_util::BodyExt;
use serde::de::DeserializeOwned;
use sqlx::{PgPool, postgres::PgPoolOptions};
use tower::ServiceExt;
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
        let prefix = admin_url
            .rsplit_once('/')
            .map_or(admin_url.as_str(), |(prefix, _)| prefix);
        let pool = PgPoolOptions::new()
            .max_connections(8)
            .connect(&format!("{prefix}/{name}"))
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

fn app(pool: PgPool) -> axum::Router {
    let settings = Settings {
        environment: "test".to_owned(),
        host: IpAddr::V4(Ipv4Addr::LOCALHOST),
        port: 0,
        database_url: "postgres://test".to_owned(),
        database_max_connections: 8,
        static_dir: PathBuf::from("../frontend"),
        allowed_origins: vec!["http://localhost".to_owned()],
        chat_channel_capacity: 32,
        max_message_bytes: 4096,
        max_connections_per_room: 20,
        shutdown_timeout: Duration::from_secs(2),
        session_ttl: Duration::from_secs(3600),
        secure_cookies: false,
        max_list_page_size: 100,
        max_history_page_size: 100,
        password_hash_concurrency: 2,
        messages_per_minute: 100,
        api_requests_per_minute: 300,
        heartbeat_interval: Duration::from_secs(10),
        heartbeat_timeout: Duration::from_secs(30),
    };
    let repository = Arc::new(PostgresChatRepository::new(pool.clone()));
    build_app(AppState::new(settings, pool, repository))
}

async fn request(
    app: axum::Router,
    method: Method,
    path: &str,
    body: Option<serde_json::Value>,
    cookie: Option<&str>,
) -> axum::response::Response {
    let mut builder = Request::builder()
        .method(method)
        .uri(path)
        .header("origin", "http://localhost");
    if body.is_some() {
        builder = builder.header("content-type", "application/json");
    }
    if let Some(cookie) = cookie {
        builder = builder.header("cookie", cookie);
    }
    app.oneshot(
        builder
            .body(body.map_or_else(Body::empty, |value| Body::from(value.to_string())))
            .unwrap(),
    )
    .await
    .unwrap()
}

async fn json<T: DeserializeOwned>(response: axum::response::Response) -> T {
    serde_json::from_slice(&response.into_body().collect().await.unwrap().to_bytes()).unwrap()
}

fn cookie(response: &axum::response::Response) -> String {
    response
        .headers()
        .get("set-cookie")
        .unwrap()
        .to_str()
        .unwrap()
        .split(';')
        .next()
        .unwrap()
        .to_owned()
}

async fn register(app: axum::Router, username: &str, password: &str) -> (String, SessionIdentity) {
    let response = request(
        app,
        Method::POST,
        "/api/auth/register",
        Some(serde_json::json!({"username": username, "password": password})),
        None,
    )
    .await;
    assert_eq!(response.status(), StatusCode::CREATED);
    let session_cookie = cookie(&response);
    (session_cookie, json(response).await)
}

#[tokio::test]
async fn complete_http_lifecycle_enforces_auth_roles_and_crud() {
    let Some(database) = TestDatabase::create().await else {
        return;
    };
    let app = app(database.pool.clone());
    let password = "correct horse 42";
    let (owner_cookie, _owner) = register(app.clone(), "Ada", password).await;
    let (member_cookie, member) = register(app.clone(), "Grace", password).await;
    let (invitee_cookie, invitee) = register(app.clone(), "Linus", password).await;

    let invalid = request(
        app.clone(),
        Method::POST,
        "/api/rooms",
        Some(serde_json::json!({"name": " ", "description": "", "is_private": false})),
        Some(&owner_cookie),
    )
    .await;
    assert_eq!(invalid.status(), StatusCode::BAD_REQUEST);

    let created = request(
        app.clone(),
        Method::POST,
        "/api/rooms",
        Some(
            serde_json::json!({"name": "Engineering", "description": "Build", "is_private": false}),
        ),
        Some(&owner_cookie),
    )
    .await;
    assert_eq!(created.status(), StatusCode::CREATED);
    let room: Room = json(created).await;

    let listed = request(
        app.clone(),
        Method::GET,
        "/api/rooms",
        None,
        Some(&owner_cookie),
    )
    .await;
    let views: Vec<RoomView> = json(listed).await;
    assert!(
        views
            .iter()
            .any(|view| view.room.id == room.id && view.current_user_role == Some(RoomRole::Owner))
    );
    let fetched: RoomView = json(
        request(
            app.clone(),
            Method::GET,
            &format!("/api/rooms/{}", room.id),
            None,
            Some(&owner_cookie),
        )
        .await,
    )
    .await;
    assert_eq!(fetched.room.name, "Engineering");
    let one_room: Vec<RoomView> = json(
        request(
            app.clone(),
            Method::GET,
            "/api/rooms?offset=0&limit=1",
            None,
            Some(&owner_cookie),
        )
        .await,
    )
    .await;
    assert_eq!(one_room.len(), 1);

    let joined = request(
        app.clone(),
        Method::POST,
        &format!("/api/rooms/{}/join", room.id),
        Some(serde_json::json!({})),
        Some(&member_cookie),
    )
    .await;
    assert_eq!(json::<Membership>(joined).await.role, RoomRole::Member);

    let forbidden_update = request(
        app.clone(),
        Method::PUT,
        &format!("/api/rooms/{}", room.id),
        Some(serde_json::json!({"name": "Nope", "description": "", "is_private": false})),
        Some(&member_cookie),
    )
    .await;
    assert_eq!(forbidden_update.status(), StatusCode::FORBIDDEN);

    let added: Membership = json(
        request(
            app.clone(),
            Method::POST,
            &format!("/api/rooms/{}/members", room.id),
            Some(serde_json::json!({"username": "Linus", "role": "member"})),
            Some(&owner_cookie),
        )
        .await,
    )
    .await;
    assert_eq!(added.user_id, invitee.user.id);
    let owner_escalation = request(
        app.clone(),
        Method::PUT,
        &format!("/api/rooms/{}/members/{}", room.id, invitee.user.id),
        Some(serde_json::json!({"role": "owner"})),
        Some(&owner_cookie),
    )
    .await;
    assert_eq!(owner_escalation.status(), StatusCode::CONFLICT);
    let promoted: Membership = json(
        request(
            app.clone(),
            Method::PUT,
            &format!("/api/rooms/{}/members/{}", room.id, invitee.user.id),
            Some(serde_json::json!({"role": "moderator"})),
            Some(&owner_cookie),
        )
        .await,
    )
    .await;
    assert_eq!(promoted.role, RoomRole::Moderator);
    let promoted_grace: Membership = json(
        request(
            app.clone(),
            Method::PUT,
            &format!("/api/rooms/{}/members/{}", room.id, member.user.id),
            Some(serde_json::json!({"role": "moderator"})),
            Some(&owner_cookie),
        )
        .await,
    )
    .await;
    assert_eq!(promoted_grace.role, RoomRole::Moderator);
    let moderator_cannot_remove_moderator = request(
        app.clone(),
        Method::DELETE,
        &format!("/api/rooms/{}/members/{}", room.id, member.user.id),
        None,
        Some(&invitee_cookie),
    )
    .await;
    assert_eq!(
        moderator_cannot_remove_moderator.status(),
        StatusCode::FORBIDDEN
    );
    let demoted_grace: Membership = json(
        request(
            app.clone(),
            Method::PUT,
            &format!("/api/rooms/{}/members/{}", room.id, member.user.id),
            Some(serde_json::json!({"role": "member"})),
            Some(&owner_cookie),
        )
        .await,
    )
    .await;
    assert_eq!(demoted_grace.role, RoomRole::Member);
    let members: Vec<Membership> = json(
        request(
            app.clone(),
            Method::GET,
            &format!("/api/rooms/{}/members", room.id),
            None,
            Some(&member_cookie),
        )
        .await,
    )
    .await;
    assert_eq!(members.len(), 3);
    let one_member: Vec<Membership> = json(
        request(
            app.clone(),
            Method::GET,
            &format!("/api/rooms/{}/members?offset=0&limit=1", room.id),
            None,
            Some(&member_cookie),
        )
        .await,
    )
    .await;
    assert_eq!(one_member.len(), 1);

    let updated: Room = json(request(
        app.clone(), Method::PUT, &format!("/api/rooms/{}", room.id),
        Some(serde_json::json!({"name": "Platform", "description": "Private build", "is_private": true})),
        Some(&owner_cookie),
    ).await).await;
    assert!(updated.is_private);

    let client_message_id = Uuid::new_v4();
    let message_response = request(
        app.clone(),
        Method::POST,
        &format!("/api/rooms/{}/messages", room.id),
        Some(serde_json::json!({"client_message_id": client_message_id, "content": "Hello API"})),
        Some(&owner_cookie),
    )
    .await;
    assert_eq!(message_response.status(), StatusCode::CREATED);
    let message: ChatMessage = json(message_response).await;
    let retry = request(
        app.clone(),
        Method::POST,
        &format!("/api/rooms/{}/messages", room.id),
        Some(serde_json::json!({"client_message_id": client_message_id, "content": "ignored"})),
        Some(&owner_cookie),
    )
    .await;
    assert_eq!(retry.status(), StatusCode::OK);
    assert_eq!(json::<ChatMessage>(retry).await.id, message.id);

    let page: MessagePage = json(
        request(
            app.clone(),
            Method::GET,
            &format!("/api/rooms/{}/messages?limit=10", room.id),
            None,
            Some(&member_cookie),
        )
        .await,
    )
    .await;
    assert_eq!(page.messages.len(), 1);
    let fetched_message: ChatMessage = json(
        request(
            app.clone(),
            Method::GET,
            &format!("/api/rooms/{}/messages/{}", room.id, message.id),
            None,
            Some(&member_cookie),
        )
        .await,
    )
    .await;
    assert_eq!(fetched_message.content, "Hello API");
    let edited: ChatMessage = json(
        request(
            app.clone(),
            Method::PUT,
            &format!("/api/rooms/{}/messages/{}", room.id, message.id),
            Some(serde_json::json!({"content": "Moderated edit"})),
            Some(&invitee_cookie),
        )
        .await,
    )
    .await;
    assert_eq!(edited.content, "Moderated edit");

    let invitee_room: Room = json(
        request(
            app.clone(),
            Method::POST,
            "/api/rooms",
            Some(serde_json::json!({
                "name": "Disposable owned room",
                "description": "Removed with its owner",
                "is_private": true
            })),
            Some(&invitee_cookie),
        )
        .await,
    )
    .await;
    let retained_message: ChatMessage = json(
        request(
            app.clone(),
            Method::POST,
            &format!("/api/rooms/{}/messages", room.id),
            Some(serde_json::json!({
                "client_message_id": Uuid::new_v4(),
                "content": "Retained after account deletion"
            })),
            Some(&invitee_cookie),
        )
        .await,
    )
    .await;

    let audit: Vec<AuditEvent> = json(
        request(
            app.clone(),
            Method::GET,
            &format!("/api/rooms/{}/audit?limit=100", room.id),
            None,
            Some(&owner_cookie),
        )
        .await,
    )
    .await;
    assert!(audit.iter().any(|event| event.action == "message.updated"));
    let deleted_message = request(
        app.clone(),
        Method::DELETE,
        &format!("/api/rooms/{}/messages/{}", room.id, message.id),
        None,
        Some(&owner_cookie),
    )
    .await;
    assert_eq!(deleted_message.status(), StatusCode::NO_CONTENT);

    let removed = request(
        app.clone(),
        Method::DELETE,
        &format!("/api/rooms/{}/members/{}", room.id, invitee.user.id),
        None,
        Some(&owner_cookie),
    )
    .await;
    assert_eq!(removed.status(), StatusCode::NO_CONTENT);
    let deleted_account = request(
        app.clone(),
        Method::DELETE,
        "/api/users/me",
        Some(serde_json::json!({"password": password})),
        Some(&invitee_cookie),
    )
    .await;
    assert_eq!(deleted_account.status(), StatusCode::NO_CONTENT);
    let deleted_owned_room = request(
        app.clone(),
        Method::GET,
        &format!("/api/rooms/{}", invitee_room.id),
        None,
        Some(&owner_cookie),
    )
    .await;
    assert_eq!(deleted_owned_room.status(), StatusCode::NOT_FOUND);
    let retained: ChatMessage = json(
        request(
            app.clone(),
            Method::GET,
            &format!("/api/rooms/{}/messages/{}", room.id, retained_message.id),
            None,
            Some(&owner_cookie),
        )
        .await,
    )
    .await;
    assert_eq!(retained.sender, "Linus");
    let reused_username = register(app.clone(), "Linus", password).await;
    assert_eq!(reused_username.1.user.username, "Linus");

    let profile: User = json(
        request(
            app.clone(),
            Method::PUT,
            "/api/users/me",
            Some(serde_json::json!({"username": "Ada Updated"})),
            Some(&owner_cookie),
        )
        .await,
    )
    .await;
    assert_eq!(profile.username, "Ada Updated");
    let changed = request(
        app.clone(), Method::POST, "/api/users/me/password",
        Some(serde_json::json!({"current_password": password, "new_password": "new secure password 84"})),
        Some(&owner_cookie),
    ).await;
    assert_eq!(changed.status(), StatusCode::NO_CONTENT);
    let login = request(
        app.clone(),
        Method::POST,
        "/api/auth/login",
        Some(serde_json::json!({"username": "Ada Updated", "password": "new secure password 84"})),
        None,
    )
    .await;
    assert_eq!(login.status(), StatusCode::OK);
    let refreshed_cookie = cookie(&login);

    let deleted_room = request(
        app.clone(),
        Method::DELETE,
        &format!("/api/rooms/{}", room.id),
        None,
        Some(&refreshed_cookie),
    )
    .await;
    assert_eq!(deleted_room.status(), StatusCode::NO_CONTENT);
    let logout = request(
        app.clone(),
        Method::POST,
        "/api/auth/logout",
        None,
        Some(&member_cookie),
    )
    .await;
    assert_eq!(logout.status(), StatusCode::NO_CONTENT);
    let protected = request(app, Method::GET, "/api/auth/me", None, Some(&member_cookie)).await;
    assert_eq!(protected.status(), StatusCode::UNAUTHORIZED);
    assert_eq!(member.user.username, "Grace");

    database.cleanup().await;
}
