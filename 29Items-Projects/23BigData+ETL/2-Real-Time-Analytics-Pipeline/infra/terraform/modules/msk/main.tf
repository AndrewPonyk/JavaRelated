# AWS MSK — the Kafka backbone. 3 brokers across 3 AZs, TLS in transit, IAM auth.
# Broker config carries the exactly-once-critical settings (transaction timeout ceiling).

resource "aws_msk_configuration" "this" {
  name           = "${var.cluster_name}-config"
  kafka_versions = [var.kafka_version]

  # min.insync.replicas=2 with RF=3: one broker can die without stopping acks=all producers.
  # transaction.max.timeout.ms must stay ABOVE the Flink sink's transaction.timeout.ms
  # (600000 in KafkaConfig.java) — see docs/TECH-NOTES.md §3.6.1.
  server_properties = <<-PROPS
    auto.create.topics.enable=false
    default.replication.factor=3
    min.insync.replicas=2
    num.partitions=1
    transaction.max.timeout.ms=900000
    log.retention.hours=168
  PROPS
}

resource "aws_cloudwatch_log_group" "broker" {
  name              = "/msk/${var.cluster_name}/broker"
  retention_in_days = 30
  tags              = var.tags
}

resource "aws_msk_cluster" "this" {
  cluster_name           = var.cluster_name
  kafka_version          = var.kafka_version
  number_of_broker_nodes = var.broker_count

  broker_node_group_info {
    instance_type   = var.instance_type
    client_subnets  = var.subnet_ids
    security_groups = var.security_group_ids

    storage_info {
      ebs_storage_info {
        volume_size = var.ebs_volume_size_gb
        # TODO(scale): provisioned throughput for >250 MiB/s per broker
      }
    }
  }

  client_authentication {
    sasl {
      iam = true # per-topic IAM policies: jobs get least-privilege produce/consume
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
    # At-rest encryption uses the AWS-managed KMS key by default; TODO: CMK for prod.
  }

  configuration_info {
    arn      = aws_msk_configuration.this.arn
    revision = aws_msk_configuration.this.latest_revision
  }

  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.broker.name
      }
    }
  }

  tags = var.tags
}
