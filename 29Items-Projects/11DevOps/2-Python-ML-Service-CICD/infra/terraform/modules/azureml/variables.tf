variable "workspace_name" {
  type        = string
  description = "Azure ML workspace name, e.g. mlw-fraud-dev."
}

variable "resource_group_name" {
  type        = string
  description = "Resource group in which to create the workspace and its dependencies."
}

variable "location" {
  type        = string
  description = "Azure region."
}

variable "key_vault_id" {
  type        = string
  description = "Resource ID of the Key Vault the workspace stores its secrets in."
}

variable "storage_account_name" {
  type        = string
  description = "Name for the workspace's default storage account (lowercase alphanumeric, globally unique)."
}

variable "application_insights_name" {
  type        = string
  description = "Name for the workspace's Application Insights component."
}

variable "tags" {
  type        = map(string)
  description = "Tags applied to all resources in this module."
  default     = {}
}
