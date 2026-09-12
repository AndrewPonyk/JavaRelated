variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "environment" {
  type        = string
  description = "Deployment environment (staging | prod)."
  validation {
    condition     = contains(["staging", "prod"], var.environment)
    error_message = "environment must be 'staging' or 'prod'."
  }
}

variable "eks_cluster_version" {
  type    = string
  default = "1.30"
}

# TODO: add variables for instance types, node pool sizing, RDS class, etc.
