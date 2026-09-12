# ADR-0001: Delta Lake medallion architecture on S3

- **Status:** Accepted
- **Date:** 2026-07-02

## Context

We need governed, audited data layers with ACID guarantees, large-scale Spark
processing, interactive SQL for analysts, and direct access for ML training —
without duplicating storage between a warehouse and a lake.

## Decision

Single storage substrate: **Delta Lake tables on S3**, organized as Bronze
(raw/immutable) → Silver (validated) → Gold (marts + features). Spark owns
Bronze/Silver writes; dbt-on-Trino owns Gold marts. Compute is ephemeral
(EMR Serverless); Trino reads all layers in place.

## Alternatives considered

- **Warehouse-only (Snowflake/Redshift):** duplicate storage for ML, engine lock-in.
- **Iceberg instead of Delta:** viable; Delta chosen for maturity of Spark MERGE /
  streaming integration and the Databricks escape hatch. Revisit if Trino-side
  write support becomes a requirement.
- **Lambda architecture:** two code paths to maintain; Structured Streaming into
  Delta covers both latencies with one.

## Consequences

- (+) One copy of data, ACID, time travel, audit via transaction log.
- (+) Engines scale and fail independently of storage.
- (−) Must actively manage small files (OPTIMIZE) and VACUUM retention.
- (−) Trino needs a metastore (Glue) kept in sync with Spark-created tables.
