variable "aws_region" {
  description = "AWS region (must be HIPAA-eligible)."
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Deployment environment."
  type        = string
  default     = "staging"
  validation {
    condition     = contains(["staging", "production"], var.environment)
    error_message = "environment must be staging or production."
  }
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t3.medium"
}

variable "api_desired_count" {
  description = "Desired ECS task count for the API service."
  type        = number
  default     = 2
}
