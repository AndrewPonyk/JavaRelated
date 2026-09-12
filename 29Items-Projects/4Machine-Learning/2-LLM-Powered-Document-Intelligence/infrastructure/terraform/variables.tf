variable "aws_region" {
  description = "AWS region for all resources."
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Deployment environment (dev | staging | prod)."
  type        = string
  default     = "dev"
}

variable "bedrock_model_ids" {
  description = "Bedrock Claude model IDs the app is allowed to invoke (anthropic.* prefix)."
  type        = list(string)
  default = [
    "anthropic.claude-opus-4-8",
    "anthropic.claude-sonnet-4-6",
    "anthropic.claude-haiku-4-5",
    "amazon.titan-embed-text-v2:0",
  ]
}

variable "documents_bucket_name" {
  description = "S3 bucket for raw uploaded documents."
  type        = string
  default     = "docintel-documents"
}
