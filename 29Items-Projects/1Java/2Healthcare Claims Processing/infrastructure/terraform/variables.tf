# Healthcare Claims Processing - Terraform Variables

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging, or prod."
  }
}

variable "location" {
  description = "Azure region for resources"
  type        = string
  default     = "eastus"
}

# Database
variable "db_sku_name" {
  description = "PostgreSQL SKU name"
  type        = string
  default     = "B_Standard_B1ms"
}

variable "db_storage_mb" {
  description = "PostgreSQL storage in MB"
  type        = number
  default     = 32768
}

# Event Hubs (Kafka)
variable "eventhubs_sku" {
  description = "Event Hubs SKU"
  type        = string
  default     = "Standard"
}

variable "eventhubs_capacity" {
  description = "Event Hubs throughput units"
  type        = number
  default     = 1
}

# Azure Functions
variable "function_sku" {
  description = "Function App service plan SKU"
  type        = string
  default     = "Y1" # Consumption plan
}

# Search (Elasticsearch alternative)
variable "search_sku" {
  description = "Azure Cognitive Search SKU"
  type        = string
  default     = "basic"
}

# Redis
variable "redis_capacity" {
  description = "Redis cache capacity"
  type        = number
  default     = 0
}

variable "redis_family" {
  description = "Redis cache family"
  type        = string
  default     = "C"
}

variable "redis_sku" {
  description = "Redis cache SKU"
  type        = string
  default     = "Basic"
}
