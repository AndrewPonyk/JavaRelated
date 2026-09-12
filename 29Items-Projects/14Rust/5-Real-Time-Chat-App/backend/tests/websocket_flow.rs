use std::{
    env,
    net::{IpAddr, Ipv4Addr},
    path::{Path, PathBuf},
    sync::Arc,
    time::Duration,
};

use axum::{
    body::Body,
    http::{HeaderValue, Method, Request, StatusCode},
};
use chat_backend::{
    build_app, config::Settings, domain::Room, repositories::PostgresChatRepository,
    state::AppState,
};
use chat_shared::{ClientCommand, ServerEvent};
use futures_util::{SinkExt, StreamExt};
use http_body_util::BodyExt;
use sqlx::{PgPool, postgres::PgPoolOptions};
use tokio_tungstenite::{
    connect_async,
    tungstenite::{Message, client::IntoClientRequest},
};
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

fn settings(database_url: String) -> Settings {
    Settings {
        environment: "test".to_owned(),
        host: IpAddr::V4(Ipv4Addr::LOCALHOST),
        port: 0,
        database_url,
        database_max_connections: 8,
        static_dir: PathBuf::from("../frontend"),
        allowed_origins: vec!["http://localhost".to_owned()],
        chat_channel_capacity: 32,
        max_message_bytes: 4096,
        max_connections_per_room: 8,
        shutdown_timeout: Duration::from_secs(2),
        session_ttl: Duration::from_secs(3600),
        secure_cookies: false,
        max_list_page_size: 100,
        max_history_page_size: 100,
        password_hash_concurrency: 2,
        messages_per_minute: 60,
        api_requests_per_minute: 300,
        heartbeat_interval: Duration::from_millis(100),
        heartbeat_timeout: Duration::from_secs(3),
    }
}

async fn api_json(
    app: axum::Router,
    method: Method,
    path: &str,
    body: serde_json::Value,
    cookie: Option<&str>,
) -> axum::response::Response {
    let mut request = Request::builder()
        .method(method)
        .uri(path)
        .header("content-type", "application/json")
        .header("origin", "http://localhost");
    if let Some(cookie) = cookie {
        request = request.header("cookie", cookie);
    }
    app.oneshot(request.body(Body::from(body.to_string())).unwrap())
        .await
        .unwrap()
}

async fn register(app: axum::Router, username: &str) -> String {
    let response = api_json(
        app,
        Method::POST,
        "/api/auth/register",
        serde_json::json!({"username": username, "password": "correct horse 42"}),
        None,
    )
    .await;
    assert_eq!(response.status(), StatusCode::CREATED);
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

async fn next_created<S>(stream: &mut S) -> chat_shared::ChatMessage
where
    S: StreamExt<Item = Result<Message, tokio_tungstenite::tungstenite::Error>> + Unpin,
{
    tokio::time::timeout(Duration::from_secs(5), async {
        loop {
            let message = stream.next().await.unwrap().unwrap();
            if let Message::Text(text) = message
                && let ServerEvent::MessageCreated { message } =
                    serde_json::from_str(&text).unwrap()
            {
                return message;
            }
        }
    })
    .await
    .expect("message event arrives")
}

#[tokio::test]
async fn two_authenticated_clients_receive_the_same_durable_message() {
    let Some(database) = TestDatabase::create().await else {
        return;
    };
    let repository = Arc::new(PostgresChatRepository::new(database.pool.clone()));
    let state = AppState::new(
        settings("postgres://test".to_owned()),
        database.pool.clone(),
        repository,
    );
    let shutdown = state.shutdown.clone();
    let app = build_app(state);

    let ada_cookie = register(app.clone(), "Ada").await;
    let grace_cookie = register(app.clone(), "Grace").await;
    let response = api_json(
        app.clone(),
        Method::POST,
        "/api/rooms",
        serde_json::json!({"name": "Engineering", "description": "Build", "is_private": false}),
        Some(&ada_cookie),
    )
    .await;
    assert_eq!(response.status(), StatusCode::CREATED);
    let room: Room =
        serde_json::from_slice(&response.into_body().collect().await.unwrap().to_bytes()).unwrap();
    let joined = api_json(
        app.clone(),
        Method::POST,
        &format!("/api/rooms/{}/join", room.id),
        serde_json::json!({}),
        Some(&grace_cookie),
    )
    .await;
    assert_eq!(joined.status(), StatusCode::OK);

    let listener = tokio::net::TcpListener::bind((Ipv4Addr::LOCALHOST, 0))
        .await
        .unwrap();
    let address = listener.local_addr().unwrap();
    let control_app = app.clone();
    let server = tokio::spawn(async move { axum::serve(listener, app).await.unwrap() });

    let connect = |cookie: &str| {
        let mut request = format!("ws://{address}/ws/{}", room.id)
            .into_client_request()
            .unwrap();
        request
            .headers_mut()
            .insert("origin", HeaderValue::from_static("http://localhost"));
        request
            .headers_mut()
            .insert("cookie", HeaderValue::from_str(cookie).unwrap());
        request
    };
    let (mut ada_socket, _) = connect_async(connect(&ada_cookie)).await.unwrap();
    let (mut grace_socket, _) = connect_async(connect(&grace_cookie)).await.unwrap();
    let command = ClientCommand::SendMessage {
        client_message_id: Uuid::new_v4(),
        content: "Hello from a real socket".to_owned(),
    };
    ada_socket
        .send(Message::Text(
            serde_json::to_string(&command).unwrap().into(),
        ))
        .await
        .unwrap();

    let from_ada = next_created(&mut ada_socket).await;
    let from_grace = next_created(&mut grace_socket).await;
    assert_eq!(from_ada, from_grace);
    assert_eq!(from_ada.content, "Hello from a real socket");

    let logout = api_json(
        control_app,
        Method::POST,
        "/api/auth/logout",
        serde_json::json!({}),
        Some(&grace_cookie),
    )
    .await;
    assert_eq!(logout.status(), StatusCode::NO_CONTENT);
    let close = tokio::time::timeout(Duration::from_secs(2), async {
        loop {
            match grace_socket.next().await {
                Some(Ok(Message::Close(frame))) => break frame,
                Some(Ok(_)) => {}
                other => panic!("expected policy close, got {other:?}"),
            }
        }
    })
    .await
    .expect("revoked session closes the socket")
    .expect("close frame exists");
    assert_eq!(u16::from(close.code), 1008);

    ada_socket.close(None).await.unwrap();
    shutdown.cancel();
    server.abort();
    database.cleanup().await;
}
