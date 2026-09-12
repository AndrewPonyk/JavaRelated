# MySQL backup and restore exercise

Use a fresh staging database and synthetic data. Never overwrite an existing environment.

1. Record the current Flyway version and application image digest.
2. Create a provider snapshot or run `mysqldump --single-transaction --routines --triggers inventory` with a least-privilege backup account.
3. Restore into a newly provisioned private MySQL instance.
4. Point one isolated backend deployment at the restored database with Kafka listeners and Quartz startup disabled.
5. Verify readiness, Flyway validation, inventory counts, ledger reconciliation, and representative barcode lookups.
6. Record recovery point, recovery duration, row counts, reconciliation result, and the operator/reviewer.
7. Destroy the isolated restored instance according to the staging retention policy.

Do not run reverse Flyway migrations during recovery. Restore the database, deploy the matching application, then apply forward-compatible migrations.
