ALTER TABLE chat_messages
    DROP CONSTRAINT IF EXISTS chat_messages_content_not_blank;

ALTER TABLE chat_messages
    ADD CONSTRAINT chat_messages_content_not_blank
    CHECK (deleted_at IS NOT NULL OR length(btrim(content)) > 0);

COMMENT ON COLUMN chat_messages.deleted_at IS
    'Soft-deletion timestamp. Deleted rows retain identity and ordering while content is erased.';
