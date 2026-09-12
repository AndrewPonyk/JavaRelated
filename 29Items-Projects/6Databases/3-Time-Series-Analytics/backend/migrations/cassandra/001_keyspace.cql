-- 001: keyspace
-- Dev/local: SimpleStrategy RF1.
-- Production (multi-node): switch BEFORE loading data, e.g.
--   ALTER KEYSPACE tsa WITH replication =
--     {'class': 'NetworkTopologyStrategy', 'dc1': 3};

CREATE KEYSPACE IF NOT EXISTS tsa
  WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
  AND durable_writes = true;
