# PROD — mirrors envs/dev/main.tf; every sizing/HA flag at production values:
#   MSK m7g.large ×3 (provisioned throughput), RDS multi_az + deletion protection,
#   OpenSearch dedicated masters, per-AZ NAT, WAF, CloudFront, deletion protection everywhere.
# Applies are gated by the GitHub 'production' environment (terraform.yml).
# TODO(phase-2): materialize after staging burns in.

terraform {
  required_version = ">= 1.9"
}
