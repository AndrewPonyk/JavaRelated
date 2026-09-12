-- Fixture, not shipped. Its only job is to be byte-different from its CRLF twin
-- while normalising to the same text, so the checksum can be shown to ignore that.
CREATE TABLE IF NOT EXISTS fixture_line_endings (id INTEGER PRIMARY KEY);
