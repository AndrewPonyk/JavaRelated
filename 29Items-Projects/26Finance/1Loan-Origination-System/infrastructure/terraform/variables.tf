variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "List of availability zones"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b", "us-east-1c"]
}

variable "rds_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.r6i.xlarge"
}

variable "eks_node_instance_types" {
  description = "EC2 instance types for EKS nodes"
  type        = list(string)
  default     = ["t3.xlarge"]
}

variable "eks_desired_capacity" {
  description = "Desired number of EKS nodes"
  type        = number
  default     = 3
}

variable "eks_min_capacity" {
  description = "Minimum number of EKS nodes"
  type        = number
  default     = 3
}

variable "eks_max_capacity" {
  description = "Maximum number of EKS nodes"
  type        = number
  default     = 10
}
