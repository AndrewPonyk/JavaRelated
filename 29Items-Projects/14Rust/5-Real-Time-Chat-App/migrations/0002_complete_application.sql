ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE users
SET password_hash = '!legacy-account-disabled!'
WHERE password_hash IS NULL;

ALTER TABLE users
    ALTER COLUMN password_hash SET NOT NULL;

ALTER TABLE chat_rooms
    ADD COLUMN IF NOT EXISTS description VARCHAR(280) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS is_private BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE TABLE sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    user_agent VARCHAR(256)
);

CREATE INDEX sessions_user_active_idx
    ON sessions (user_id, expires_at DESC)
    WHERE revoked_at IS NULL;

ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS client_message_id UUID,
    ADD COLUMN IF NOT EXISTS edited_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

DO $$
DECLARE
    legacy_user_id UUID;
BEGIN
    IF EXISTS (SELECT 1 FROM chat_messages WHERE sender_user_id IS NULL) THEN
        INSERT INTO users (username, username_normalized, password_hash, is_active)
        VALUES ('Legacy User', 'legacy-user', '!legacy-account-disabled!', FALSE)
        ON CONFLICT (username_normalized)
        DO UPDATE SET is_active = FALSE
        RETURNING id INTO legacy_user_id;

        UPDATE chat_messages
        SET sender_user_id = legacy_user_id
        WHERE sender_user_id IS NULL;
    END IF;
END $$;

UPDATE chat_messages
SET client_message_id = gen_random_uuid()
WHERE client_message_id IS NULL;

ALTER TABLE chat_messages
    ALTER COLUMN sender_user_id SET NOT NULL,
    ALTER COLUMN client_message_id SET NOT NULL;

CREATE UNIQUE INDEX chat_messages_idempotency_idx
    ON chat_messages (room_id, sender_user_id, client_message_id);

CREATE TABLE audit_events (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    room_id UUID REFERENCES chat_rooms(id) ON DELETE SET NULL,
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id UUID,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX audit_events_room_created_idx
    ON audit_events (room_id, created_at DESC);

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS users_set_updated_at ON users;
CREATE TRIGGER users_set_updated_at
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS chat_rooms_set_updated_at ON chat_rooms;
CREATE TRIGGER chat_rooms_set_updated_at
BEFORE UPDATE ON chat_rooms
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE FUNCTION prevent_last_room_owner_removal()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.role = 'owner'
       AND NOT EXISTS (
           SELECT 1
           FROM room_memberships
           WHERE room_id = OLD.room_id
             AND user_id <> OLD.user_id
             AND role = 'owner'
       )
       AND EXISTS (
           SELECT 1
           FROM chat_rooms
           WHERE id = OLD.room_id
             AND owner_user_id IS NOT NULL
       )
    THEN
        RAISE EXCEPTION 'a room must retain an owner' USING ERRCODE = '23514';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS room_memberships_keep_owner ON room_memberships;
CREATE TRIGGER room_memberships_keep_owner
BEFORE DELETE ON room_memberships
FOR EACH ROW EXECUTE FUNCTION prevent_last_room_owner_removal();
