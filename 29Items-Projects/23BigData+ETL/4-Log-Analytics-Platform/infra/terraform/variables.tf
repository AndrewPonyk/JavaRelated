variable "environment" {
  description = "Deployment environment (dev | staging | prod)"
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be dev, staging or prod"
  }
}

variable "region" {
  description = "AWS region"
  type        = string
  default     = "eu-central-1"
}

variable "vpc_id" {
  description = "VPC hosting MSK/OpenSearch/ECS (TODO: dedicated VPC module or shared platform VPC)"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnets across 3 AZs"
  type        = list(string)
}

variable "kafka_broker_instance_type" {
  type    = string
  default = "kafka.m7g.large"
}

variable "opensearch_instance_type" {
  type    = string
  default = "r7g.large.search"
}

variable "opensearch_instance_count" {
  type    = number
  default = 3
}

variable "opensearch_volume_size_gb" {
  type    = number
  default = 200
}

variable "emr_max_cpu" {
  description = "EMR Serverless application maximum vCPU"
  type        = string
  default     = "64 vCPU"
}

variable "emr_max_memory" {
  type    = string
  default = "256 GB"
}

variable "github_repository" {
  description = "GitHub org/repo allowed to assume the CI/CD roles via OIDC"
  type        = string
  default     = "your-org/log-analytics-platform" # TODO
}
