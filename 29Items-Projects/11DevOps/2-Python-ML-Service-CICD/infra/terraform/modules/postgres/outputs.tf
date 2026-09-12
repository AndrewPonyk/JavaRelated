output "server_id" {
  description = "Resource ID of the flexible server."
  value       = azurerm_postgresql_flexible_server.main.id
}

output "fqdn" {
  description = "Server FQDN — host part of FRAUD_DATABASE_URL."
  value       = azurerm_postgresql_flexible_server.main.fqdn
}

output "database_name" {
  description = "Application database name."
  value       = azurerm_postgresql_flexible_server_database.fraud.name
}

output "administrator_login" {
  description = "Admin login name."
  value       = azurerm_postgresql_flexible_server.main.administrator_login
}
