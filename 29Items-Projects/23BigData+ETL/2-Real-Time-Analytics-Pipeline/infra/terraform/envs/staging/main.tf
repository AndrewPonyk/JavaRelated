# STAGING — mirrors envs/dev/main.tf with production-like sizing.
# TODO(phase-2): copy the dev composition once its module interfaces settle;
# differences live in tfvars only (msk m7g.large, RDS multi_az, OpenSearch dedicated masters).

terraform {
  required_version = ">= 1.9"
}
