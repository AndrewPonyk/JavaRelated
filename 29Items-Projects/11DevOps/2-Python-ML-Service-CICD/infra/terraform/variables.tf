# ---------------------------------------------------------------------------
# Root input variables. Per-environment values live in environments/*.tfvars.
# ---------------------------------------------------------------------------

variable "environment" {
  type        = string
  description = "Deployment environment. One of: dev, staging, prod."

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be one of: dev, staging, prod."
  }
}

variable "location" {
  type        = string
  description = "Azure region for all resources."
  default     = "westeurope"
}

variable "project_name" {
  type        = string
  description = "Project slug used in resource names (rg-<project_name>-<env>, ...)."
  default     = "fraud-detection"
}

variable "tags" {
  type        = map(string)
  description = "Extra tags merged onto every resource."
  default     = {}
}

variable "node_count" {
  type        = number
  description = "Number of nodes in the AKS default (system) node pool."
  default     = 2
}

variable "node_vm_size" {
  type        = string
  description = "VM size for the AKS default node pool."
  default     = "Standard_D2s_v5"
}

variable "postgres_sku" {
  type        = string
  description = "PostgreSQL Flexible Server SKU (e.g. B_Standard_B1ms, GP_Standard_D2s_v3)."
  default     = "B_Standard_B1ms"
}

variable "postgres_storage_mb" {
  type        = number
  description = "PostgreSQL Flexible Server storage in MB (min 32768)."
  default     = 32768
}
