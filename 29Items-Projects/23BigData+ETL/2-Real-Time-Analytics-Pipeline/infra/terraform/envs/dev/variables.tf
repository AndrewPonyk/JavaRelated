variable "environment" {
  type    = string
  default = "dev"
}

variable "region" {
  type    = string
  default = "eu-central-1"
}

variable "azs" {
  type    = list(string)
  default = ["eu-central-1a", "eu-central-1b", "eu-central-1c"]
}

variable "msk_instance_type" {
  type    = string
  default = "kafka.t3.small" # dev sizing; m7g.large upward in staging/prod
}

variable "aggregation_jar_key" {
  type        = string
  description = "S3 key of the aggregation job jar (CD writes flink/<sha>/…)"
}

variable "anomaly_jar_key" {
  type = string
}

variable "api_image" {
  type        = string
  description = "ECR image URI:tag for analytics-api"
}
