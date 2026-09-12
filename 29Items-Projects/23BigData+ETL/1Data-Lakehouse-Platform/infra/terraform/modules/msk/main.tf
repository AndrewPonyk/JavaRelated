# Kafka via MSK Serverless — pay-per-use fits bursty ingestion; move to
# provisioned MSK when sustained throughput makes it cheaper.
# The cluster is created only when networking inputs are provided, so the
# stack can be applied incrementally (storage first, streaming later).

variable "project" { type = string }
variable "environment" { type = string }
variable "vpc_id" { type = string }
variable "private_subnet_ids" { type = list(string) }

variable "client_security_group_ids" {
  description = "Security groups allowed to reach the brokers (EMR jobs, MWAA workers)"
  type        = list(string)
  default     = []
}

locals {
  networked = var.vpc_id != "" && length(var.private_subnet_ids) > 0
}

resource "aws_security_group" "msk" {
  count       = local.networked ? 1 : 0
  name        = "${var.environment}-${var.project}-msk"
  description = "MSK access from platform compute only"
  vpc_id      = var.vpc_id
}

resource "aws_security_group_rule" "client_ingress" {
  count                    = local.networked ? length(var.client_security_group_ids) : 0
  type                     = "ingress"
  from_port                = 9098 # SASL/IAM listener
  to_port                  = 9098
  protocol                 = "tcp"
  security_group_id        = aws_security_group.msk[0].id
  source_security_group_id = var.client_security_group_ids[count.index]
}

resource "aws_msk_serverless_cluster" "this" {
  count        = local.networked ? 1 : 0
  cluster_name = "${var.environment}-${var.project}"

  client_authentication {
    sasl {
      iam {
        enabled = true
      }
    }
  }

  vpc_config {
    subnet_ids         = var.private_subnet_ids
    security_group_ids = [aws_security_group.msk[0].id]
  }
}

# Topics are application-managed (scripts/create_kafka_topics.sh locally; the
# same naming/retention conventions apply on MSK): orders.v1 (12 partitions,
# 7-day retention), orders.v1.dlq, platform.audit.v1.

output "bootstrap_brokers" {
  value = local.networked ? aws_msk_serverless_cluster.this[0].bootstrap_brokers_sasl_iam : ""
}
