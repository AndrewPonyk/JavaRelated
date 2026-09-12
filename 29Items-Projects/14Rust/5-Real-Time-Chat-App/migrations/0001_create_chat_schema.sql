CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(40) NOT NULL,
    username_normalized VARCHAR(40) NOT NULL UNIQUE,
    password_hash TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT users_username_not_blank CHECK (length(btrim(username)) > 0)
);

CREATE TABLE chat_rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(80) NOT NULL,
    name_normalized VARCHAR(80) NOT NULL UNIQUE,
    owner_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chat_rooms_name_not_blank CHECK (length(btrim(name)) > 0)
);

CREATE TABLE room_memberships (
    room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL DEFAULT 'member',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (room_id, user_id),
    CONSTRAINT room_memberships_valid_role CHECK (role IN ('member', 'moderator', 'owner'))
);

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    sender_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    sender_display_name VARCHAR(40) NOT NULL,
    content VARCHAR(4096) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chat_messages_sender_not_blank CHECK (length(btrim(sender_display_name)) > 0),
    CONSTRAINT chat_messages_content_not_blank CHECK (length(btrim(content)) > 0)
);

CREATE INDEX chat_messages_room_history_idx
    ON chat_messages (room_id, created_at DESC, id DESC);

CREATE INDEX room_memberships_user_idx
    ON room_memberships (user_id, joined_at DESC);

INSERT INTO chat_rooms (name, name_normalized)
VALUES ('General', 'general')
ON CONFLICT (name_normalized) DO NOTHING;
