CREATE INDEX IF NOT EXISTS sessions_expiry_idx
    ON sessions (expires_at);

CREATE INDEX IF NOT EXISTS sessions_revoked_idx
    ON sessions (revoked_at)
    WHERE revoked_at IS NOT NULL;

DROP INDEX IF EXISTS audit_events_room_created_idx;
CREATE INDEX IF NOT EXISTS audit_events_room_id_desc_idx
    ON audit_events (room_id, id DESC);

INSERT INTO room_memberships (room_id, user_id, role)
SELECT id, owner_user_id, 'owner'
FROM chat_rooms
WHERE owner_user_id IS NOT NULL
ON CONFLICT (room_id, user_id) DO UPDATE SET role = 'owner';

UPDATE room_memberships AS membership
SET role = 'moderator'
FROM chat_rooms AS room
WHERE membership.room_id = room.id
  AND membership.role = 'owner'
  AND room.owner_user_id IS DISTINCT FROM membership.user_id;

CREATE OR REPLACE FUNCTION enforce_room_owner_membership()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.role = 'owner'
       AND NOT EXISTS (
           SELECT 1
           FROM chat_rooms
           WHERE id = NEW.room_id
             AND owner_user_id = NEW.user_id
       )
    THEN
        RAISE EXCEPTION 'owner membership must match chat_rooms.owner_user_id'
            USING ERRCODE = '23514';
    END IF;

    IF TG_OP = 'UPDATE' AND OLD.role = 'owner' AND NEW.role <> 'owner' THEN
        RAISE EXCEPTION 'the canonical room owner cannot be demoted'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS room_memberships_owner_integrity ON room_memberships;
CREATE TRIGGER room_memberships_owner_integrity
BEFORE INSERT OR UPDATE ON room_memberships
FOR EACH ROW EXECUTE FUNCTION enforce_room_owner_membership();
