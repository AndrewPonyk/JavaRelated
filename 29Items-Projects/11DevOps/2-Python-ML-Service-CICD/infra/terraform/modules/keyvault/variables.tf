variable "name" {
  type        = string
  description = "Key Vault name (3-24 chars, globally unique), e.g. kv-fraud-dev."
}

variable "resource_group_name" {
  type        = string
  description = "Resource group in which to create the vault."
}

variable "location" {
  type        = string
  description = "Azure region."
}

variable "sku_name" {
  type        = string
  description = "Vault SKU: standard or premium."
  default     = "standard"

  validation {
    condition     = contains(["standard", "premium"], var.sku_name)
    error_message = "sku_name must be one of: standard, premium."
  }
}

variable "purge_protection_enabled" {
  type        = bool
  description = "Enable purge protection (irreversible once on; recommended for prod)."
  default     = false
}

variable "tags" {
  type        = map(string)
  description = "Tags applied to the vault."
  default     = {}
}
