# AWS MSK cluster — IAM auth, TLS in transit, CloudWatch broker logs.

resource "aws_security_group" "msk" {
  name_prefix = "${var.name}-msk-"
  vpc_id      = var.vpc_id

  ingress {
    description = "Kafka IAM/TLS from inside the VPC"
    from_port   = 9098
    to_port     = 9098
    protocol    = "tcp"
    cidr_blocks = [var.client_cidr]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = var.tags
}

resource "aws_cloudwatch_log_group" "broker" {
  name              = "/msk/${var.name}"
  retention_in_days = 30
  tags              = var.tags
}

resource "aws_msk_cluster" "this" {
  cluster_name           = var.name
  kafka_version          = var.kafka_version
  number_of_broker_nodes = var.broker_count

  broker_node_group_info {
    instance_type   = var.instance_type
    client_subnets  = var.subnet_ids
    security_groups = [aws_security_group.msk.id]

    storage_info {
      ebs_storage_info {
        volume_size = var.ebs_volume_size_gb
      }
    }
  }

  client_authentication {
    sasl {
      iam = true # no broker passwords anywhere — task roles authenticate
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
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

# TODO(Phase 2): aws_mskconnect_connector for the Snowflake Kafka connector
# (Snowpipe Streaming into RAW.EVENTS.STREAM_EVENTS) + topic provisioning
# (terraform kafka provider or an init job): events.orders.v1, alerts.anomaly.v1,
# events.deadletter.v1.
