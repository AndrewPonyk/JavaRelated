variable "project_id" {
  type        = string
  description = "GCP project id"
}

variable "region" {
  type    = string
  default = "europe-west1"
}

variable "environment" {
  type        = string
  description = "dev | staging | prod"
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be dev, staging or prod"
  }
}

variable "elasticsearch_version" {
  type    = string
  default = "8.14.3"
}

variable "es_node_size" {
  type    = string
  default = "2g"
}

variable "es_zone_count" {
  type    = number
  default = 1
}

variable "db_tier" {
  type    = string
  default = "db-custom-1-3840"
}

variable "redis_memory_gb" {
  type    = number
  default = 1
}

variable "api_min_instances" {
  type    = number
  default = 0
}

variable "api_max_instances" {
  type    = number
  default = 10
}
