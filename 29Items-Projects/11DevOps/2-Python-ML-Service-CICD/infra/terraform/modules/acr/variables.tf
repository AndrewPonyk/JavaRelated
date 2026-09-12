variable "name" {
  type        = string
  description = "Registry name (alphanumeric only, globally unique), e.g. frauddetectacr."
}

variable "resource_group_name" {
  type        = string
  description = "Resource group in which to create the registry."
}

variable "location" {
  type        = string
  description = "Azure region."
}

variable "sku" {
  type        = string
  description = "Registry SKU: Basic, Standard or Premium."
  default     = "Standard"

  validation {
    condition     = contains(["Basic", "Standard", "Premium"], var.sku)
    error_message = "sku must be one of: Basic, Standard, Premium."
  }
}

variable "tags" {
  type        = map(string)
  description = "Tags applied to the registry."
  default     = {}
}
