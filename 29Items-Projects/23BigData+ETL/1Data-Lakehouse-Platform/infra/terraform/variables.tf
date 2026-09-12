variable "project" {
  description = "Project slug used in resource names"
  type        = string
  default     = "lakehouse"
}

variable "environment" {
  description = "Deployment environment"
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be dev, staging, or prod."
  }
}

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "eu-central-1"
}

variable "kms_key_arn" {
  description = "KMS key for lake encryption (empty = SSE-S3)"
  type        = string
  default     = ""
}

variable "vpc_id" {
  description = "VPC for MSK networking (empty defers cluster creation)"
  type        = string
  default     = ""
}

variable "private_subnet_ids" {
  description = "Private subnets for MSK brokers"
  type        = list(string)
  default     = []
}

variable "kafka_client_security_group_ids" {
  description = "Security groups allowed to reach Kafka (EMR, MWAA)"
  type        = list(string)
  default     = []
}
