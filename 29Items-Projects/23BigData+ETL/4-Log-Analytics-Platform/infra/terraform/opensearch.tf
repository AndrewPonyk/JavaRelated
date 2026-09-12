# AWS OpenSearch domain — storage, search, dashboards.

resource "aws_security_group" "opensearch" {
  name_prefix = "${local.name_prefix}-os-"
  vpc_id      = var.vpc_id

  # TODO: ingress 443 from ECS tasks, EMR Serverless, and the deploy runner path only.
}

resource "aws_opensearch_domain" "logs" {
  domain_name    = local.name_prefix
  engine_version = "OpenSearch_2.17" # keep in step with docker-compose image

  cluster_config {
    instance_type          = var.opensearch_instance_type
    instance_count         = var.opensearch_instance_count
    zone_awareness_enabled = var.opensearch_instance_count >= 2

    dynamic "zone_awareness_config" {
      for_each = var.opensearch_instance_count >= 3 ? [1] : []
      content {
        availability_zone_count = 3
      }
    }

    # TODO(prod): dedicated_master_enabled = true (3 × m7g.medium.search)
    # TODO(Phase 3): warm_enabled + UltraWarm for >7d data
  }

  ebs_options {
    ebs_enabled = true
    volume_type = "gp3"
    volume_size = var.opensearch_volume_size_gb
  }

  vpc_options {
    subnet_ids         = slice(var.private_subnet_ids, 0, min(var.opensearch_instance_count, 3))
    security_group_ids = [aws_security_group.opensearch.id]
  }

  encrypt_at_rest {
    enabled = true
  }

  node_to_node_encryption {
    enabled = true
  }

  domain_endpoint_options {
    enforce_https       = true
    tls_security_policy = "Policy-Min-TLS-1-2-PFS-2023-10"
  }

  # TODO(prod): advanced_security_options (fine-grained access control, internal DB off,
  #             IAM master role) + access_policies restricted to task/job roles.
  # TODO: snapshot policy to S3 (ISM handles retention, snapshots handle disasters).
}
