use async_trait::async_trait;
use chat_shared::ChatMessage;
use chrono::{DateTime, Utc};
use sqlx::{PgPool, Postgres, Transaction};
use uuid::Uuid;

use super::ChatRepository;
use crate::{
    domain::{
        AuditEvent, Membership, MessageCreation, Room, RoomRole, RoomView, SessionIdentity, User,
        UserCredentials,
    },
    error::AppError,
};

type UserRow = (Uuid, String, DateTime<Utc>, DateTime<Utc>);
type CredentialRow = (Uuid, String, String, DateTime<Utc>, DateTime<Utc>);
type SessionRow = (
    Uuid,
    Uuid,
    String,
    DateTime<Utc>,
    DateTime<Utc>,
    DateTime<Utc>,
);
type RoomRow = (
    Uuid,
    String,
    String,
    bool,
    Option<Uuid>,
    DateTime<Utc>,
    DateTime<Utc>,
);
type RoomViewRow = (
    Uuid,
    String,
    String,
    bool,
    Option<Uuid>,
    DateTime<Utc>,
    DateTime<Utc>,
    i64,
    Option<String>,
);
type MembershipRow = (Uuid, Uuid, String, String, DateTime<Utc>);
type MessageRow = (
    Uuid,
    Uuid,
    Uuid,
    Uuid,
    String,
    String,
    DateTime<Utc>,
    Option<DateTime<Utc>>,
    Option<DateTime<Utc>>,
);
type MessageCreationRow = (
    Uuid,
    Uuid,
    Uuid,
    Uuid,
    String,
    String,
    DateTime<Utc>,
    Option<DateTime<Utc>>,
    Option<DateTime<Utc>>,
    bool,
);
type AuditRow = (
    i64,
    Option<Uuid>,
    Option<Uuid>,
    String,
    String,
    Option<Uuid>,
    DateTime<Utc>,
);

#[derive(Debug, Clone)]
pub struct PostgresChatRepository {
    pool: PgPool,
}

impl PostgresChatRepository {
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

#[async_trait]
impl ChatRepository for PostgresChatRepository {
    async fn create_user(
        &self,
        username: &str,
        normalized_username: &str,
        password_hash: &str,
    ) -> Result<User, AppError> {
        sqlx::query_as::<_, UserRow>(
            r#"
            INSERT INTO users (username, username_normalized, password_hash)
            VALUES ($1, $2, $3)
            RETURNING id, username, created_at, updated_at
            "#,
        )
        .bind(username)
        .bind(normalized_username)
        .bind(password_hash)
        .fetch_one(&self.pool)
        .await
        .map(user_from_row)
        .map_err(map_write_error)
    }

    async fn find_user_by_normalized_username(
        &self,
        normalized_username: &str,
    ) -> Result<Option<UserCredentials>, AppError> {
        sqlx::query_as::<_, CredentialRow>(
            r#"
            SELECT id, username, password_hash, created_at, updated_at
            FROM users
            WHERE username_normalized = $1 AND is_active = TRUE
            "#,
        )
        .bind(normalized_username)
        .fetch_optional(&self.pool)
        .await
        .map(|row| row.map(credentials_from_row))
        .map_err(AppError::Database)
    }

    async fn get_user(&self, user_id: Uuid) -> Result<Option<User>, AppError> {
        sqlx::query_as::<_, UserRow>(
            "SELECT id, username, created_at, updated_at FROM users WHERE id = $1 AND is_active = TRUE",
        )
        .bind(user_id)
        .fetch_optional(&self.pool)
        .await
        .map(|row| row.map(user_from_row))
        .map_err(AppError::Database)
    }

    async fn update_user(
        &self,
        user_id: Uuid,
        username: &str,
        normalized_username: &str,
    ) -> Result<Option<User>, AppError> {
        sqlx::query_as::<_, UserRow>(
            r#"
            UPDATE users
            SET username = $2, username_normalized = $3
            WHERE id = $1 AND is_active = TRUE
            RETURNING id, username, created_at, updated_at
            "#,
        )
        .bind(user_id)
        .bind(username)
        .bind(normalized_username)
        .fetch_optional(&self.pool)
        .await
        .map(|row| row.map(user_from_row))
        .map_err(map_write_error)
    }

    async fn update_password(&self, user_id: Uuid, password_hash: &str) -> Result<(), AppError> {
        let result =
            sqlx::query("UPDATE users SET password_hash = $2 WHERE id = $1 AND is_active = TRUE")
                .bind(user_id)
                .bind(password_hash)
                .execute(&self.pool)
                .await
                .map_err(AppError::Database)?;
        if result.rows_affected() == 0 {
            return Err(AppError::NotFound("user"));
        }
        Ok(())
    }

    async fn delete_user(&self, user_id: Uuid) -> Result<Option<Vec<Uuid>>, AppError> {
        let mut transaction = self.pool.begin().await.map_err(AppError::Database)?;
        let deleted_room_ids = sqlx::query_scalar::<_, Uuid>(
            "DELETE FROM chat_rooms WHERE owner_user_id = $1 RETURNING id",
        )
        .bind(user_id)
        .fetch_all(&mut *transaction)
        .await
        .map_err(map_write_error)?;
        sqlx::query("DELETE FROM room_memberships WHERE user_id = $1")
            .bind(user_id)
            .execute(&mut *transaction)
            .await
            .map_err(map_write_error)?;
        sqlx::query("DELETE FROM sessions WHERE user_id = $1")
            .bind(user_id)
            .execute(&mut *transaction)
            .await
            .map_err(map_write_error)?;
        let anonymized = format!("deleted-{}", user_id.simple());
        let result = sqlx::query(
            r#"
            UPDATE users
            SET username = $2, username_normalized = $2,
                password_hash = '!account-deleted!', is_active = FALSE
            WHERE id = $1 AND is_active = TRUE
            "#,
        )
        .bind(user_id)
        .bind(anonymized)
        .execute(&mut *transaction)
        .await
        .map_err(map_write_error)?;
        transaction.commit().await.map_err(AppError::Database)?;
        Ok((result.rows_affected() == 1).then_some(deleted_room_ids))
    }

    async fn create_session(
        &self,
        user_id: Uuid,
        token_hash: &str,
        expires_at: DateTime<Utc>,
        user_agent: Option<&str>,
    ) -> Result<SessionIdentity, AppError> {
        let row = sqlx::query_as::<_, SessionRow>(
            r#"
            WITH inserted AS (
                INSERT INTO sessions (user_id, token_hash, expires_at, user_agent)
                VALUES ($1, $2, $3, $4)
                RETURNING id, user_id, expires_at
            )
            SELECT i.id, u.id, u.username, u.created_at, u.updated_at, i.expires_at
            FROM inserted i
            JOIN users u ON u.id = i.user_id
            "#,
        )
        .bind(user_id)
        .bind(token_hash)
        .bind(expires_at)
        .bind(user_agent.map(|value| truncate(value, 256)))
        .fetch_one(&self.pool)
        .await
        .map_err(map_write_error)?;
        Ok(session_from_row(row))
    }

    async fn find_session(&self, token_hash: &str) -> Result<Option<SessionIdentity>, AppError> {
        let row = sqlx::query_as::<_, SessionRow>(
            r#"
            WITH touched AS (
                UPDATE sessions
                SET last_seen_at = NOW()
                WHERE token_hash = $1
                  AND revoked_at IS NULL
                  AND expires_at > NOW()
                  AND last_seen_at < NOW() - INTERVAL '5 minutes'
            )
            SELECT s.id, u.id, u.username, u.created_at, u.updated_at, s.expires_at
            FROM sessions s
            JOIN users u ON s.user_id = u.id
            WHERE s.token_hash = $1
              AND s.user_id = u.id
              AND s.revoked_at IS NULL
              AND s.expires_at > NOW()
              AND u.is_active = TRUE
            "#,
        )
        .bind(token_hash)
        .fetch_optional(&self.pool)
        .await
        .map_err(AppError::Database)?;
        Ok(row.map(session_from_row))
    }

    async fn revoke_session(&self, token_hash: &str) -> Result<(), AppError> {
        sqlx::query(
            "UPDATE sessions SET revoked_at = COALESCE(revoked_at, NOW()) WHERE token_hash = $1",
        )
        .bind(token_hash)
        .execute(&self.pool)
        .await
        .map_err(AppError::Database)?;
        Ok(())
    }

    async fn revoke_all_sessions(&self, user_id: Uuid) -> Result<(), AppError> {
        sqlx::query(
            "UPDATE sessions SET revoked_at = COALESCE(revoked_at, NOW()) WHERE user_id = $1",
        )
        .bind(user_id)
        .execute(&self.pool)
        .await
        .map_err(AppError::Database)?;
        Ok(())
    }

    async fn delete_expired_sessions(&self) -> Result<u64, AppError> {
        sqlx::query("DELETE FROM sessions WHERE expires_at <= NOW() OR revoked_at IS NOT NULL")
            .execute(&self.pool)
            .await
            .map(|result| result.rows_affected())
            .map_err(AppError::Database)
    }

    async fn list_rooms(
        &self,
        current_user_id: Option<Uuid>,
        offset: i64,
        limit: i64,
    ) -> Result<Vec<RoomView>, AppError> {
        let rows = sqlx::query_as::<_, RoomViewRow>(
            r#"
            SELECT r.id, r.name, r.description, r.is_private, r.owner_user_id,
                   r.created_at, r.updated_at, COUNT(m.user_id)::BIGINT,
                   current_membership.role
            FROM chat_rooms r
            LEFT JOIN room_memberships m ON m.room_id = r.id
            LEFT JOIN room_memberships current_membership
                   ON current_membership.room_id = r.id
                  AND current_membership.user_id = $1::UUID
            WHERE r.is_private = FALSE OR current_membership.user_id IS NOT NULL
            GROUP BY r.id, current_membership.role
            ORDER BY r.name_normalized ASC, r.id ASC
            OFFSET $2 LIMIT $3
            "#,
        )
        .bind(current_user_id)
        .bind(offset)
        .bind(limit)
        .fetch_all(&self.pool)
        .await
        .map_err(AppError::Database)?;
        rows.into_iter().map(room_view_from_row).collect()
    }

    async fn get_room(&self, room_id: Uuid) -> Result<Option<Room>, AppError> {
        sqlx::query_as::<_, RoomRow>(
            r#"
            SELECT id, name, description, is_private, owner_user_id, created_at, updated_at
            FROM chat_rooms WHERE id = $1
            "#,
        )
        .bind(room_id)
        .fetch_optional(&self.pool)
        .await
        .map(|row| row.map(room_from_row))
        .map_err(AppError::Database)
    }

    async fn get_room_view(
        &self,
        room_id: Uuid,
        current_user_id: Option<Uuid>,
    ) -> Result<Option<RoomView>, AppError> {
        let row = sqlx::query_as::<_, RoomViewRow>(
            r#"
            SELECT r.id, r.name, r.description, r.is_private, r.owner_user_id,
                   r.created_at, r.updated_at, COUNT(m.user_id)::BIGINT,
                   current_membership.role
            FROM chat_rooms r
            LEFT JOIN room_memberships m ON m.room_id = r.id
            LEFT JOIN room_memberships current_membership
                   ON current_membership.room_id = r.id
                  AND current_membership.user_id = $2::UUID
            WHERE r.id = $1
              AND (r.is_private = FALSE OR current_membership.user_id IS NOT NULL)
            GROUP BY r.id, current_membership.role
            "#,
        )
        .bind(room_id)
        .bind(current_user_id)
        .fetch_optional(&self.pool)
        .await
        .map_err(AppError::Database)?;
        row.map(room_view_from_row).transpose()
    }

    async fn create_room(
        &self,
        owner_user_id: Uuid,
        name: &str,
        normalized_name: &str,
        description: &str,
        is_private: bool,
    ) -> Result<Room, AppError> {
        let mut transaction = self.pool.begin().await.map_err(AppError::Database)?;
        let row = sqlx::query_as::<_, RoomRow>(
            r#"
            INSERT INTO chat_rooms
                (name, name_normalized, description, is_private, owner_user_id)
            VALUES ($1, $2, $3, $4, $5)
            RETURNING id, name, description, is_private, owner_user_id, created_at, updated_at
            "#,
        )
        .bind(name)
        .bind(normalized_name)
        .bind(description)
        .bind(is_private)
        .bind(owner_user_id)
        .fetch_one(&mut *transaction)
        .await
        .map_err(map_write_error)?;
        sqlx::query(
            "INSERT INTO room_memberships (room_id, user_id, role) VALUES ($1, $2, 'owner')",
        )
        .bind(row.0)
        .bind(owner_user_id)
        .execute(&mut *transaction)
        .await
        .map_err(map_write_error)?;
        insert_audit(
            &mut transaction,
            Some(owner_user_id),
            Some(row.0),
            "room.created",
            "room",
            Some(row.0),
        )
        .await?;
        transaction.commit().await.map_err(AppError::Database)?;
        Ok(room_from_row(row))
    }

    async fn update_room(
        &self,
        room_id: Uuid,
        name: &str,
        normalized_name: &str,
        description: &str,
        is_private: bool,
    ) -> Result<Option<Room>, AppError> {
        sqlx::query_as::<_, RoomRow>(
            r#"
            UPDATE chat_rooms
            SET name = $2, name_normalized = $3, description = $4, is_private = $5
            WHERE id = $1
            RETURNING id, name, description, is_private, owner_user_id, created_at, updated_at
            "#,
        )
        .bind(room_id)
        .bind(name)
        .bind(normalized_name)
        .bind(description)
        .bind(is_private)
        .fetch_optional(&self.pool)
        .await
        .map(|row| row.map(room_from_row))
        .map_err(map_write_error)
    }

    async fn delete_room(&self, room_id: Uuid) -> Result<bool, AppError> {
        let result = sqlx::query("DELETE FROM chat_rooms WHERE id = $1")
            .bind(room_id)
            .execute(&self.pool)
            .await
            .map_err(map_write_error)?;
        Ok(result.rows_affected() == 1)
    }

    async fn get_membership(
        &self,
        room_id: Uuid,
        user_id: Uuid,
    ) -> Result<Option<Membership>, AppError> {
        membership_query(
            r#"
            SELECT m.room_id, m.user_id, u.username, m.role, m.joined_at
            FROM room_memberships m
            JOIN users u ON u.id = m.user_id
            WHERE m.room_id = $1 AND m.user_id = $2
            "#,
            room_id,
            user_id,
            &self.pool,
        )
        .await
    }

    async fn list_members(
        &self,
        room_id: Uuid,
        offset: i64,
        limit: i64,
    ) -> Result<Vec<Membership>, AppError> {
        let rows = sqlx::query_as::<_, MembershipRow>(
            r#"
            SELECT m.room_id, m.user_id, u.username, m.role, m.joined_at
            FROM room_memberships m
            JOIN users u ON u.id = m.user_id
            WHERE m.room_id = $1
            ORDER BY CASE m.role WHEN 'owner' THEN 0 WHEN 'moderator' THEN 1 ELSE 2 END,
                     u.username_normalized, u.id
            OFFSET $2 LIMIT $3
            "#,
        )
        .bind(room_id)
        .bind(offset)
        .bind(limit)
        .fetch_all(&self.pool)
        .await
        .map_err(AppError::Database)?;
        rows.into_iter().map(membership_from_row).collect()
    }

    async fn add_member_by_username(
        &self,
        room_id: Uuid,
        normalized_username: &str,
        role: RoomRole,
    ) -> Result<Membership, AppError> {
        let user_id = sqlx::query_scalar::<_, Uuid>(
            "SELECT id FROM users WHERE username_normalized = $1 AND is_active = TRUE",
        )
        .bind(normalized_username)
        .fetch_optional(&self.pool)
        .await
        .map_err(AppError::Database)?
        .ok_or(AppError::NotFound("user"))?;
        sqlx::query(
            r#"
            INSERT INTO room_memberships (room_id, user_id, role)
            VALUES ($1, $2, $3)
            ON CONFLICT (room_id, user_id) DO NOTHING
            "#,
        )
        .bind(room_id)
        .bind(user_id)
        .bind(role.as_str())
        .execute(&self.pool)
        .await
        .map_err(map_write_error)?;
        self.get_membership(room_id, user_id)
            .await?
            .ok_or(AppError::Conflict(
                "membership could not be created".to_owned(),
            ))
    }

    async fn join_room(&self, room_id: Uuid, user_id: Uuid) -> Result<Membership, AppError> {
        let inserted = sqlx::query_scalar::<_, Uuid>(
            r#"
            INSERT INTO room_memberships (room_id, user_id, role)
            SELECT id, $2, 'member' FROM chat_rooms WHERE id = $1 AND is_private = FALSE
            ON CONFLICT (room_id, user_id) DO UPDATE SET user_id = EXCLUDED.user_id
            RETURNING user_id
            "#,
        )
        .bind(room_id)
        .bind(user_id)
        .fetch_optional(&self.pool)
        .await
        .map_err(map_write_error)?;
        if inserted.is_none() {
            return Err(AppError::Forbidden(
                "private rooms require an invitation".to_owned(),
            ));
        }
        self.get_membership(room_id, user_id)
            .await?
            .ok_or(AppError::Internal)
    }

    async fn update_member_role(
        &self,
        room_id: Uuid,
        user_id: Uuid,
        role: RoomRole,
    ) -> Result<Option<Membership>, AppError> {
        let updated = sqlx::query_scalar::<_, Uuid>(
            r#"
            UPDATE room_memberships SET role = $3
            WHERE room_id = $1 AND user_id = $2
            RETURNING user_id
            "#,
        )
        .bind(room_id)
        .bind(user_id)
        .bind(role.as_str())
        .fetch_optional(&self.pool)
        .await
        .map_err(map_write_error)?;
        match updated {
            Some(_) => self.get_membership(room_id, user_id).await,
            None => Ok(None),
        }
    }

    async fn remove_member(&self, room_id: Uuid, user_id: Uuid) -> Result<bool, AppError> {
        let result =
            sqlx::query("DELETE FROM room_memberships WHERE room_id = $1 AND user_id = $2")
                .bind(room_id)
                .bind(user_id)
                .execute(&self.pool)
                .await
                .map_err(map_write_error)?;
        Ok(result.rows_affected() == 1)
    }

    async fn list_messages(
        &self,
        room_id: Uuid,
        before: Option<(DateTime<Utc>, Uuid)>,
        limit: i64,
    ) -> Result<Vec<ChatMessage>, AppError> {
        let rows = match before {
            Some((created_at, id)) => {
                sqlx::query_as::<_, MessageRow>(
                    r#"
                    SELECT m.id, m.client_message_id, m.room_id, m.sender_user_id,
                           m.sender_display_name,
                           CASE WHEN m.deleted_at IS NULL THEN m.content ELSE '' END,
                           m.created_at, m.edited_at, m.deleted_at
                    FROM chat_messages m
                    WHERE m.room_id = $1 AND (m.created_at, m.id) < ($2, $3)
                    ORDER BY m.created_at DESC, m.id DESC
                    LIMIT $4
                    "#,
                )
                .bind(room_id)
                .bind(created_at)
                .bind(id)
                .bind(limit)
                .fetch_all(&self.pool)
                .await
            }
            None => {
                sqlx::query_as::<_, MessageRow>(
                    r#"
                    SELECT m.id, m.client_message_id, m.room_id, m.sender_user_id,
                           m.sender_display_name,
                           CASE WHEN m.deleted_at IS NULL THEN m.content ELSE '' END,
                           m.created_at, m.edited_at, m.deleted_at
                    FROM chat_messages m
                    WHERE m.room_id = $1
                    ORDER BY m.created_at DESC, m.id DESC
                    LIMIT $2
                    "#,
                )
                .bind(room_id)
                .bind(limit)
                .fetch_all(&self.pool)
                .await
            }
        }
        .map_err(AppError::Database)?;
        Ok(rows.into_iter().map(message_from_row).collect())
    }

    async fn get_message(&self, message_id: Uuid) -> Result<Option<ChatMessage>, AppError> {
        sqlx::query_as::<_, MessageRow>(
            r#"
            SELECT id, client_message_id, room_id, sender_user_id, sender_display_name,
                   CASE WHEN deleted_at IS NULL THEN content ELSE '' END,
                   created_at, edited_at, deleted_at
            FROM chat_messages WHERE id = $1
            "#,
        )
        .bind(message_id)
        .fetch_optional(&self.pool)
        .await
        .map(|row| row.map(message_from_row))
        .map_err(AppError::Database)
    }

    async fn create_message(
        &self,
        room_id: Uuid,
        sender_user_id: Uuid,
        sender: &str,
        client_message_id: Uuid,
        content: &str,
    ) -> Result<MessageCreation, AppError> {
        sqlx::query_as::<_, MessageCreationRow>(
            r#"
            INSERT INTO chat_messages
                (room_id, sender_user_id, sender_display_name, client_message_id, content)
            VALUES ($1, $2, $3, $4, $5)
            ON CONFLICT (room_id, sender_user_id, client_message_id)
            DO UPDATE SET client_message_id = EXCLUDED.client_message_id
            RETURNING id, client_message_id, room_id, sender_user_id, sender_display_name,
                      CASE WHEN deleted_at IS NULL THEN content ELSE '' END,
                      created_at, edited_at, deleted_at, (xmax = 0) AS created
            "#,
        )
        .bind(room_id)
        .bind(sender_user_id)
        .bind(sender)
        .bind(client_message_id)
        .bind(content)
        .fetch_one(&self.pool)
        .await
        .map(message_creation_from_row)
        .map_err(map_write_error)
    }

    async fn update_message(
        &self,
        message_id: Uuid,
        content: &str,
    ) -> Result<Option<ChatMessage>, AppError> {
        sqlx::query_as::<_, MessageRow>(
            r#"
            UPDATE chat_messages
            SET content = $2, edited_at = NOW()
            WHERE id = $1 AND deleted_at IS NULL
            RETURNING id, client_message_id, room_id, sender_user_id, sender_display_name,
                      content, created_at, edited_at, deleted_at
            "#,
        )
        .bind(message_id)
        .bind(content)
        .fetch_optional(&self.pool)
        .await
        .map(|row| row.map(message_from_row))
        .map_err(AppError::Database)
    }

    async fn delete_message(&self, message_id: Uuid) -> Result<Option<ChatMessage>, AppError> {
        sqlx::query_as::<_, MessageRow>(
            r#"
            UPDATE chat_messages
            SET content = '', deleted_at = COALESCE(deleted_at, NOW())
            WHERE id = $1
            RETURNING id, client_message_id, room_id, sender_user_id, sender_display_name,
                      '', created_at, edited_at, deleted_at
            "#,
        )
        .bind(message_id)
        .fetch_optional(&self.pool)
        .await
        .map(|row| row.map(message_from_row))
        .map_err(AppError::Database)
    }

    async fn audit(
        &self,
        actor_user_id: Option<Uuid>,
        room_id: Option<Uuid>,
        action: &str,
        target_type: &str,
        target_id: Option<Uuid>,
    ) -> Result<(), AppError> {
        sqlx::query(
            r#"
            INSERT INTO audit_events
                (actor_user_id, room_id, action, target_type, target_id)
            VALUES ($1, $2, $3, $4, $5)
            "#,
        )
        .bind(actor_user_id)
        .bind(room_id)
        .bind(action)
        .bind(target_type)
        .bind(target_id)
        .execute(&self.pool)
        .await
        .map_err(AppError::Database)?;
        Ok(())
    }

    async fn list_audit_events(
        &self,
        room_id: Uuid,
        before_id: Option<i64>,
        limit: i64,
    ) -> Result<Vec<AuditEvent>, AppError> {
        sqlx::query_as::<_, AuditRow>(
            r#"
            SELECT id, actor_user_id, room_id, action, target_type, target_id, created_at
            FROM audit_events
            WHERE room_id = $1 AND ($2::BIGINT IS NULL OR id < $2)
            ORDER BY id DESC
            LIMIT $3
            "#,
        )
        .bind(room_id)
        .bind(before_id)
        .bind(limit)
        .fetch_all(&self.pool)
        .await
        .map(|rows| {
            rows.into_iter()
                .map(
                    |(id, actor_user_id, room_id, action, target_type, target_id, created_at)| {
                        AuditEvent {
                            id,
                            actor_user_id,
                            room_id,
                            action,
                            target_type,
                            target_id,
                            created_at,
                        }
                    },
                )
                .collect()
        })
        .map_err(AppError::Database)
    }
}

async fn membership_query(
    query: &str,
    room_id: Uuid,
    user_id: Uuid,
    pool: &PgPool,
) -> Result<Option<Membership>, AppError> {
    let row = sqlx::query_as::<_, MembershipRow>(query)
        .bind(room_id)
        .bind(user_id)
        .fetch_optional(pool)
        .await
        .map_err(AppError::Database)?;
    row.map(membership_from_row).transpose()
}

async fn insert_audit(
    transaction: &mut Transaction<'_, Postgres>,
    actor_user_id: Option<Uuid>,
    room_id: Option<Uuid>,
    action: &str,
    target_type: &str,
    target_id: Option<Uuid>,
) -> Result<(), AppError> {
    sqlx::query(
        r#"
        INSERT INTO audit_events (actor_user_id, room_id, action, target_type, target_id)
        VALUES ($1, $2, $3, $4, $5)
        "#,
    )
    .bind(actor_user_id)
    .bind(room_id)
    .bind(action)
    .bind(target_type)
    .bind(target_id)
    .execute(&mut **transaction)
    .await
    .map_err(AppError::Database)?;
    Ok(())
}

fn user_from_row((id, username, created_at, updated_at): UserRow) -> User {
    User {
        id,
        username,
        created_at,
        updated_at,
    }
}

fn credentials_from_row(
    (id, username, password_hash, created_at, updated_at): CredentialRow,
) -> UserCredentials {
    UserCredentials {
        user: User {
            id,
            username,
            created_at,
            updated_at,
        },
        password_hash,
    }
}

fn session_from_row(
    (session_id, user_id, username, created_at, updated_at, expires_at): SessionRow,
) -> SessionIdentity {
    SessionIdentity {
        session_id,
        user: User {
            id: user_id,
            username,
            created_at,
            updated_at,
        },
        expires_at,
    }
}

fn room_from_row(
    (id, name, description, is_private, owner_user_id, created_at, updated_at): RoomRow,
) -> Room {
    Room {
        id,
        name,
        description,
        is_private,
        owner_user_id,
        created_at,
        updated_at,
    }
}

fn room_view_from_row(
    (id, name, description, is_private, owner_user_id, created_at, updated_at, member_count, role): RoomViewRow,
) -> Result<RoomView, AppError> {
    let current_user_role = role
        .as_deref()
        .map(RoomRole::try_from)
        .transpose()
        .map_err(|_| AppError::Internal)?;
    Ok(RoomView {
        room: Room {
            id,
            name,
            description,
            is_private,
            owner_user_id,
            created_at,
            updated_at,
        },
        member_count,
        online_count: 0,
        current_user_role,
    })
}

fn membership_from_row(
    (room_id, user_id, username, role, joined_at): MembershipRow,
) -> Result<Membership, AppError> {
    let role = RoomRole::try_from(role.as_str()).map_err(|_| AppError::Internal)?;
    Ok(Membership {
        room_id,
        user_id,
        username,
        role,
        joined_at,
    })
}

fn message_from_row(
    (
        id,
        client_message_id,
        room_id,
        sender_user_id,
        sender,
        content,
        sent_at,
        edited_at,
        deleted_at,
    ): MessageRow,
) -> ChatMessage {
    ChatMessage {
        id,
        client_message_id,
        room_id,
        sender_user_id,
        sender,
        content,
        sent_at,
        edited_at,
        deleted_at,
    }
}

fn message_creation_from_row(
    (
        id,
        client_message_id,
        room_id,
        sender_user_id,
        sender,
        content,
        sent_at,
        edited_at,
        deleted_at,
        created,
    ): MessageCreationRow,
) -> MessageCreation {
    MessageCreation {
        message: ChatMessage {
            id,
            client_message_id,
            room_id,
            sender_user_id,
            sender,
            content,
            sent_at,
            edited_at,
            deleted_at,
        },
        created,
    }
}

fn map_write_error(error: sqlx::Error) -> AppError {
    if let sqlx::Error::Database(database_error) = &error {
        return match database_error.code().as_deref() {
            Some("23505") => AppError::Conflict("the resource already exists".to_owned()),
            Some("23503") => AppError::Conflict("a related resource does not exist".to_owned()),
            Some("23514") => {
                AppError::Conflict("the requested state violates a data constraint".to_owned())
            }
            _ => AppError::Database(error),
        };
    }
    AppError::Database(error)
}

fn truncate(value: &str, max_chars: usize) -> String {
    value.chars().take(max_chars).collect()
}
