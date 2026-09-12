use std::{sync::Arc, time::Duration};

use argon2::{
    Argon2, PasswordHash, PasswordHasher, PasswordVerifier,
    password_hash::{SaltString, rand_core::OsRng},
};
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use chrono::Utc;
use rand_core::RngCore;
use sha2::{Digest, Sha256};
use tokio::sync::Semaphore;
use uuid::Uuid;

use crate::{
    domain::{SessionIdentity, User},
    error::AppError,
    repositories::ChatRepository,
};

#[derive(Debug)]
pub struct CreatedSession {
    pub identity: SessionIdentity,
    pub token: String,
}

#[derive(Clone)]
pub struct AuthService {
    repository: Arc<dyn ChatRepository>,
    session_ttl: Duration,
    password_workers: Arc<Semaphore>,
}

impl std::fmt::Debug for AuthService {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        formatter
            .debug_struct("AuthService")
            .finish_non_exhaustive()
    }
}

impl AuthService {
    pub fn new(
        repository: Arc<dyn ChatRepository>,
        session_ttl: Duration,
        password_hash_concurrency: usize,
    ) -> Self {
        Self {
            repository,
            session_ttl,
            password_workers: Arc::new(Semaphore::new(password_hash_concurrency)),
        }
    }

    pub async fn register(
        &self,
        username: &str,
        password: &str,
        user_agent: Option<&str>,
    ) -> Result<CreatedSession, AppError> {
        let username = normalize_username(username)?;
        validate_password(password)?;
        let password_hash =
            hash_password(password.to_owned(), self.password_workers.clone()).await?;
        let user = self
            .repository
            .create_user(&username, &username.to_lowercase(), &password_hash)
            .await?;
        self.create_session(user.id, user_agent).await
    }

    pub async fn login(
        &self,
        username: &str,
        password: &str,
        user_agent: Option<&str>,
    ) -> Result<CreatedSession, AppError> {
        let username = normalize_username(username)?;
        let credentials = self
            .repository
            .find_user_by_normalized_username(&username.to_lowercase())
            .await?;
        let Some(credentials) = credentials else {
            // Keep unknown-user attempts computationally comparable to invalid-password attempts.
            let _ = hash_password(password.to_owned(), self.password_workers.clone()).await?;
            return Err(AppError::Unauthorized);
        };
        if !verify_password(
            password.to_owned(),
            credentials.password_hash,
            self.password_workers.clone(),
        )
        .await?
        {
            return Err(AppError::Unauthorized);
        }
        let _ = self.repository.delete_expired_sessions().await;
        self.create_session(credentials.user.id, user_agent).await
    }

    pub async fn authenticate(&self, token: &str) -> Result<SessionIdentity, AppError> {
        if token.len() < 32 || token.len() > 128 {
            return Err(AppError::Unauthorized);
        }
        self.repository
            .find_session(&hash_token(token))
            .await?
            .ok_or(AppError::Unauthorized)
    }

    pub async fn logout(&self, token: &str) -> Result<(), AppError> {
        self.repository.revoke_session(&hash_token(token)).await
    }

    pub async fn update_profile(&self, user_id: Uuid, username: &str) -> Result<User, AppError> {
        let username = normalize_username(username)?;
        self.repository
            .update_user(user_id, &username, &username.to_lowercase())
            .await?
            .ok_or(AppError::NotFound("user"))
    }

    pub async fn change_password(
        &self,
        user_id: Uuid,
        username: &str,
        current_password: &str,
        new_password: &str,
    ) -> Result<(), AppError> {
        validate_password(new_password)?;
        let credentials = self
            .repository
            .find_user_by_normalized_username(&username.to_lowercase())
            .await?
            .ok_or(AppError::Unauthorized)?;
        if credentials.user.id != user_id
            || !verify_password(
                current_password.to_owned(),
                credentials.password_hash,
                self.password_workers.clone(),
            )
            .await?
        {
            return Err(AppError::Unauthorized);
        }
        let password_hash =
            hash_password(new_password.to_owned(), self.password_workers.clone()).await?;
        self.repository
            .update_password(user_id, &password_hash)
            .await?;
        self.repository.revoke_all_sessions(user_id).await
    }

    pub async fn delete_account(
        &self,
        user_id: Uuid,
        username: &str,
        password: &str,
    ) -> Result<Vec<Uuid>, AppError> {
        let credentials = self
            .repository
            .find_user_by_normalized_username(&username.to_lowercase())
            .await?
            .ok_or(AppError::Unauthorized)?;
        if credentials.user.id != user_id
            || !verify_password(
                password.to_owned(),
                credentials.password_hash,
                self.password_workers.clone(),
            )
            .await?
        {
            return Err(AppError::Unauthorized);
        }
        self.repository
            .delete_user(user_id)
            .await?
            .ok_or(AppError::NotFound("user"))
    }

    async fn create_session(
        &self,
        user_id: Uuid,
        user_agent: Option<&str>,
    ) -> Result<CreatedSession, AppError> {
        let mut bytes = [0_u8; 32];
        OsRng.fill_bytes(&mut bytes);
        let token = URL_SAFE_NO_PAD.encode(bytes);
        let ttl = chrono::Duration::from_std(self.session_ttl).map_err(|_| AppError::Internal)?;
        let identity = self
            .repository
            .create_session(user_id, &hash_token(&token), Utc::now() + ttl, user_agent)
            .await?;
        Ok(CreatedSession { identity, token })
    }
}

pub fn normalize_username(value: &str) -> Result<String, AppError> {
    let value = value.trim();
    let length = value.chars().count();
    if !(3..=40).contains(&length) {
        return Err(AppError::Validation(
            "username must contain 3 to 40 characters".to_owned(),
        ));
    }
    if value.chars().any(char::is_control)
        || !value
            .chars()
            .all(|character| character.is_alphanumeric() || " _-.".contains(character))
    {
        return Err(AppError::Validation(
            "username contains unsupported characters".to_owned(),
        ));
    }
    Ok(value.to_owned())
}

fn validate_password(password: &str) -> Result<(), AppError> {
    if password.len() < 12 || password.len() > 128 {
        return Err(AppError::Validation(
            "password must contain 12 to 128 UTF-8 bytes".to_owned(),
        ));
    }
    if !password.chars().any(char::is_alphabetic) || !password.chars().any(char::is_numeric) {
        return Err(AppError::Validation(
            "password must contain at least one letter and one number".to_owned(),
        ));
    }
    Ok(())
}

async fn hash_password(
    password: String,
    password_workers: Arc<Semaphore>,
) -> Result<String, AppError> {
    let _permit = password_workers
        .acquire_owned()
        .await
        .map_err(|_| AppError::Unavailable)?;
    tokio::task::spawn_blocking(move || {
        let salt = SaltString::generate(&mut OsRng);
        Argon2::default()
            .hash_password(password.as_bytes(), &salt)
            .map(|hash| hash.to_string())
            .map_err(|_| AppError::Internal)
    })
    .await
    .map_err(|_| AppError::Internal)?
}

async fn verify_password(
    password: String,
    encoded: String,
    password_workers: Arc<Semaphore>,
) -> Result<bool, AppError> {
    let _permit = password_workers
        .acquire_owned()
        .await
        .map_err(|_| AppError::Unavailable)?;
    tokio::task::spawn_blocking(move || {
        let Ok(hash) = PasswordHash::new(&encoded) else {
            return Ok(false);
        };
        Ok(Argon2::default()
            .verify_password(password.as_bytes(), &hash)
            .is_ok())
    })
    .await
    .map_err(|_| AppError::Internal)?
}

pub fn hash_token(token: &str) -> String {
    format!("{:x}", Sha256::digest(token.as_bytes()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn validates_usernames_and_password_policy() {
        assert_eq!(
            normalize_username("  Ada Lovelace ").unwrap(),
            "Ada Lovelace"
        );
        assert!(normalize_username("a").is_err());
        assert!(normalize_username("bad/name").is_err());
        assert!(validate_password("short").is_err());
        assert!(validate_password("correct horse 42").is_ok());
    }

    #[tokio::test]
    async fn password_hashes_are_salted_and_verifiable() {
        let workers = Arc::new(Semaphore::new(2));
        let first = hash_password("correct horse 42".to_owned(), workers.clone())
            .await
            .unwrap();
        let second = hash_password("correct horse 42".to_owned(), workers.clone())
            .await
            .unwrap();
        assert_ne!(first, second);
        assert!(
            verify_password("correct horse 42".to_owned(), first, workers.clone())
                .await
                .unwrap()
        );
        assert!(
            !verify_password("wrong password 12".to_owned(), second, workers)
                .await
                .unwrap()
        );
    }
}
