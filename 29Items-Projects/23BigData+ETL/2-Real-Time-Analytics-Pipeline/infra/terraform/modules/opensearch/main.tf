# Amazon OpenSearch — fulfills the "Elasticsearch" slot (ADR #3): managed, IAM-integrated,
# API-compatible for our usage. Index templates: elasticsearch/index-templates/
# (remember: 'flattened' → 'flat_object' on OpenSearch — TECH-NOTES §3.6.5).

variable "domain_name" { type = string }
variable "engine_version" {
  type    = string
  default = "OpenSearch_2.17" # TODO: verify latest
}
variable "instance_type" {
  type    = string
  default = "r7g.large.search"
}
variable "instance_count" {
  type    = number
  default = 3
}
variable "volume_size_gb" {
  type    = number
  default = 100
}
variable "subnet_ids" { type = list(string) }
variable "security_group_ids" { type = list(string) }
variable "tags" {
  type    = map(string)
  default = {}
}

resource "aws_opensearch_domain" "this" {
  domain_name    = var.domain_name
  engine_version = var.engine_version

  cluster_config {
    instance_type          = var.instance_type
    instance_count         = var.instance_count
    zone_awareness_enabled = var.instance_count >= 2

    dynamic "zone_awareness_config" {
      for_each = var.instance_count >= 2 ? [1] : []
      content {
        availability_zone_count = min(var.instance_count, 3)
      }
    }
    # TODO(prod): dedicated_master_enabled = true (3 × m7g.large.search)
  }

  ebs_options {
    ebs_enabled = true
    volume_size = var.volume_size_gb
    volume_type = "gp3"
  }

  encrypt_at_rest {
    enabled = true
  }

  node_to_node_encryption {
    enabled = true
  }

  domain_endpoint_options {
    enforce_https       = true
    tls_security_policy = "Policy-Min-TLS-1-2-2019-07"
  }

  vpc_options {
    subnet_ids         = var.subnet_ids
    security_group_ids = var.security_group_ids
  }

  # TODO(security): fine-grained access control + IAM role mapping for the Flink
  # sink (write) and analytics-api/Grafana (read-only).
  # TODO(phase-3): ISM policy — hot 7d → delete 30d; UltraWarm if retention grows.

  tags = var.tags
}

output "endpoint" { value = aws_opensearch_domain.this.endpoint }
output "arn" { value = aws_opensearch_domain.this.arn }
