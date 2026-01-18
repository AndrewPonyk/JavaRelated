# Elasticsearch Module (using Azure Cognitive Search as managed alternative)

variable "resource_group_name" {
  type = string
}

variable "location" {
  type = string
}

variable "environment" {
  type = string
}

variable "sku" {
  type    = string
  default = "basic"
}

variable "tags" {
  type    = map(string)
  default = {}
}

# Azure Cognitive Search (Elasticsearch alternative)
resource "azurerm_search_service" "main" {
  name                = "claims-search-${var.environment}"
  resource_group_name = var.resource_group_name
  location            = var.location
  sku                 = var.sku
  replica_count       = var.environment == "prod" ? 2 : 1
  partition_count     = var.environment == "prod" ? 2 : 1

  public_network_access_enabled = true

  tags = var.tags
}

# For actual Elasticsearch, you would deploy on AKS or use Elastic Cloud
# This is a placeholder showing the Azure Cognitive Search alternative

# Outputs
output "endpoint" {
  value = "https://${azurerm_search_service.main.name}.search.windows.net"
}

output "admin_key" {
  value     = azurerm_search_service.main.primary_key
  sensitive = true
}

output "query_key" {
  value     = azurerm_search_service.main.query_keys[0].key
  sensitive = true
}
