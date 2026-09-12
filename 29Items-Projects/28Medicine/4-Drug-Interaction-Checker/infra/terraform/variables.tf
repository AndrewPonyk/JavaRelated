variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Deployment environment (dev|staging|prod)"
  type        = string
  default     = "dev"
}

variable "cluster_name" {
  description = "EKS cluster name"
  type        = string
  default     = "dic-eks"
}

variable "kubernetes_version" {
  description = "EKS Kubernetes version"
  type        = string
  default     = "1.30"
}

variable "model_artifact_bucket" {
  description = "S3 bucket for ML model artifacts"
  type        = string
  default     = "dic-models"
}

variable "node_instance_types" {
  description = "Managed node group instance types"
  type        = list(string)
  default     = ["t3.large"]
}
