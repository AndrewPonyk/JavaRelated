-- Sample workload for the MiniDB CLI.
-- Run interactively:   minidb demo.db   (then paste these)
-- Or in batch mode:    minidb demo.db < examples/sample_queries.sql

CREATE TABLE users (id INT, name VARCHAR(32), age INT);

INSERT INTO users VALUES (1, 'Ada',   36);
INSERT INTO users VALUES (2, 'Linus', 54);
INSERT INTO users VALUES (3, 'Grace', 85);
INSERT INTO users VALUES (4, 'Edsger', 61);
INSERT INTO users VALUES (5, 'Donald', 86);

-- Point lookup on the primary key — the planner should favor the index scan.
SELECT id, name FROM users WHERE id = 3;

-- Range query — exercises the B+ Tree range scan path.
SELECT id, name, age FROM users WHERE id >= 2;

-- Predicate on a non-indexed column — falls back to a sequential scan.
SELECT name FROM users WHERE age > 60;

-- Project every column.
SELECT * FROM users;
