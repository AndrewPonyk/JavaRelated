# PostgreSQL Flexible Server Module

variable "resource_group_name" {
  type = string
}

variable "location" {
  type = string
}

variable "environment" {
  type = string
}

variable "subnet_id" {
  type = string
}

variable "key_vault_id" {
  type = string
}

variable "sku_name" {
  type    = string
  default = "B_Standard_B1ms"
}

variable "storage_mb" {
  type    = number
  default = 32768
}

variable "admin_username" {
  type    = string
  default = "claimsadmin"
}

variable "tags" {
  type    = map(string)
  default = {}
}

# Generate random password
resource "random_password" "postgres" {
  length           = 24
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

# Store password in Key Vault
resource "azurerm_key_vault_secret" "postgres_password" {
  name         = "db-password"
  value        = random_password.postgres.result
  key_vault_id = var.key_vault_id
}

# Private DNS Zone
resource "azurerm_private_dns_zone" "postgres" {
  name                = "claims-${var.environment}.postgres.database.azure.com"
  resource_group_name = var.resource_group_name

  tags = var.tags
}

# PostgreSQL Flexible Server
resource "azurerm_postgresql_flexible_server" "main" {
  name                   = "claims-postgres-${var.environment}"
  resource_group_name    = var.resource_group_name
  location               = var.location
  version                = "15"
  administrator_login    = var.admin_username
  administrator_password = random_password.postgres.result
  storage_mb             = var.storage_mb
  sku_name               = var.sku_name
  zone                   = var.environment == "prod" ? "1" : null

  backup_retention_days        = var.environment == "prod" ? 35 : 7
  geo_redundant_backup_enabled = var.environment == "prod"

  high_availability {
    mode                      = var.environment == "prod" ? "ZoneRedundant" : "Disabled"
    standby_availability_zone = var.environment == "prod" ? "2" : null
  }

  tags = var.tags
}

# Database
resource "azurerm_postgresql_flexible_server_database" "claims" {
  name      = "claims"
  server_id = azurerm_postgresql_flexible_server.main.id
  collation = "en_US.utf8"
  charset   = "UTF8"
}

# Firewall rule for Azure services
resource "azurerm_postgresql_flexible_server_firewall_rule" "azure_services" {
  name             = "AllowAzureServices"
  server_id        = azurerm_postgresql_flexible_server.main.id
  start_ip_address = "0.0.0.0"
  end_ip_address   = "0.0.0.0"
}

# Server configuration
resource "azurerm_postgresql_flexible_server_configuration" "log_checkpoints" {
  name      = "log_checkpoints"
  server_id = azurerm_postgresql_flexible_server.main.id
  value     = "on"
}

resource "azurerm_postgresql_flexible_server_configuration" "log_connections" {
  name      = "log_connections"
  server_id = azurerm_postgresql_flexible_server.main.id
  value     = "on"
}

# Outputs
output "server_fqdn" {
  value = azurerm_postgresql_flexible_server.main.fqdn
}

output "server_name" {
  value = azurerm_postgresql_flexible_server.main.name
}

output "database_name" {
  value = azurerm_postgresql_flexible_server_database.claims.name
}

output "admin_username" {
  value = var.admin_username
}
