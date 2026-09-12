-- ============================================================================
--  db/schema.sql
--  Convenience entry point. The AUTHORITATIVE schema is the ordered set of
--  migrations under db/migrations/, applied by Flyway in CI/CD. This file just
--  bootstraps a fresh local/dev database by running them in order.
--
--  Apply (SQL*Plus / sqlcl):
--      @db/migrations/V001__initial_schema.sql
--      @db/migrations/V002__add_ml_predictions.sql
--
--  Production uses:  flyway -locations=filesystem:db/migrations migrate
-- ============================================================================

-- Seed reference data for local development.
INSERT INTO instruments (symbol_id, ticker, venue, tick_size, lot_size, active)
VALUES (1, 'AAPL', 'XNAS', 0.01, 1, 'Y');
INSERT INTO instruments (symbol_id, ticker, venue, tick_size, lot_size, active)
VALUES (2, 'ESZ5', 'XCME', 0.25, 1, 'Y');
COMMIT;
