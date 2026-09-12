"""Environment-driven settings for lakehouse jobs.

Single source of truth for bucket URIs, Kafka endpoints, and table paths.
Values come from the environment (see .env.example); safe local defaults let
every job run against the docker-compose stack with zero configuration.
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field


@dataclass(frozen=True)
class LakehouseSettings:
    bronze_uri: str
    silver_uri: str
    gold_uri: str
    artifacts_uri: str
    kafka_bootstrap_servers: str
    orders_topic: str
    dlq_topic: str
    s3_endpoint: str | None
    local_mode: bool
    # Kafka hardening (unset locally; set on MSK: SASL_SSL + AWS_MSK_IAM)
    kafka_security_protocol: str | None = None
    kafka_sasl_mechanism: str | None = None
    kafka_sasl_jaas_config: str | None = None
    kafka_max_offsets_per_trigger: str | None = None
    kafka_fail_on_data_loss: str = "false"
    # Governance catalog (empty → registration disabled, e.g. unit tests)
    catalog_api_url: str = ""
    catalog_api_token: str = field(default="", repr=False)
    catalog_default_owner: str = "data-platform@example.com"

    @classmethod
    def from_env(cls) -> LakehouseSettings:
        return cls(
            bronze_uri=os.getenv("LAKE_BRONZE_URI", "s3a://lakehouse-bronze"),
            silver_uri=os.getenv("LAKE_SILVER_URI", "s3a://lakehouse-silver"),
            gold_uri=os.getenv("LAKE_GOLD_URI", "s3a://lakehouse-gold"),
            artifacts_uri=os.getenv("LAKE_ARTIFACTS_URI", "s3a://lakehouse-artifacts"),
            kafka_bootstrap_servers=os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9094"),
            orders_topic=os.getenv("KAFKA_ORDERS_TOPIC", "orders.v1"),
            dlq_topic=os.getenv("KAFKA_DLQ_TOPIC", "orders.v1.dlq"),
            s3_endpoint=os.getenv("AWS_ENDPOINT_URL") or None,
            local_mode=os.getenv("LAKEHOUSE_LOCAL", "0") == "1",
            kafka_security_protocol=os.getenv("KAFKA_SECURITY_PROTOCOL") or None,
            kafka_sasl_mechanism=os.getenv("KAFKA_SASL_MECHANISM") or None,
            kafka_sasl_jaas_config=os.getenv("KAFKA_SASL_JAAS_CONFIG") or None,
            kafka_max_offsets_per_trigger=os.getenv("KAFKA_MAX_OFFSETS_PER_TRIGGER") or None,
            kafka_fail_on_data_loss=os.getenv("KAFKA_FAIL_ON_DATA_LOSS", "false"),
            catalog_api_url=os.getenv("CATALOG_API_URL", "").rstrip("/"),
            catalog_api_token=os.getenv("CATALOG_API_TOKEN", ""),
            catalog_default_owner=os.getenv("CATALOG_DEFAULT_OWNER", "data-platform@example.com"),
        )

    # --- Path conventions: s3://<layer>/<domain>/<table>/ -----------------

    def bronze_path(self, domain: str, table: str) -> str:
        return f"{self.bronze_uri}/{domain}/{table}"

    def silver_path(self, domain: str, table: str) -> str:
        return f"{self.silver_uri}/{domain}/{table}"

    def gold_path(self, domain: str, table: str) -> str:
        return f"{self.gold_uri}/{domain}/{table}"

    def layer_path(self, layer: str, domain: str, table: str) -> str:
        base = {"bronze": self.bronze_uri, "silver": self.silver_uri, "gold": self.gold_uri}
        return f"{base[layer]}/{domain}/{table}"

    def quarantine_path(self, domain: str, table: str) -> str:
        """Rows rejected between Bronze and Silver, kept with rejection reasons."""
        return f"{self.silver_uri}/_quarantine/{domain}/{table}"

    def checkpoint_path(self, job_name: str) -> str:
        """Structured Streaming checkpoints live with artifacts, not data."""
        return f"{self.artifacts_uri}/_checkpoints/{job_name}"

    # --- Kafka source options for Structured Streaming -----------------------

    def kafka_source_options(self) -> dict[str, str]:
        """Connection + security options, driven entirely by configuration so the
        same job runs against local plaintext Kafka and SASL-secured MSK."""
        options = {"kafka.bootstrap.servers": self.kafka_bootstrap_servers}
        if self.kafka_security_protocol:
            options["kafka.security.protocol"] = self.kafka_security_protocol
        if self.kafka_sasl_mechanism:
            options["kafka.sasl.mechanism"] = self.kafka_sasl_mechanism
        if self.kafka_sasl_jaas_config:
            options["kafka.sasl.jaas.config"] = self.kafka_sasl_jaas_config
        return options
