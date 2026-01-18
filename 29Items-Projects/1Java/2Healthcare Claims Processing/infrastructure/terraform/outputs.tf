# Healthcare Claims Processing - Terraform Outputs

output "resource_group_name" {
  description = "Name of the resource group"
  value       = azurerm_resource_group.main.name
}

output "resource_group_location" {
  description = "Location of the resource group"
  value       = azurerm_resource_group.main.location
}

output "key_vault_uri" {
  description = "URI of the Key Vault"
  value       = azurerm_key_vault.main.vault_uri
}

output "key_vault_name" {
  description = "Name of the Key Vault"
  value       = azurerm_key_vault.main.name
}

output "postgresql_server_fqdn" {
  description = "PostgreSQL server FQDN"
  value       = module.postgresql.server_fqdn
  sensitive   = true
}

output "postgresql_database_name" {
  description = "PostgreSQL database name"
  value       = module.postgresql.database_name
}

output "eventhubs_namespace" {
  description = "Event Hubs namespace name"
  value       = module.eventhubs.namespace_name
}

output "eventhubs_kafka_endpoint" {
  description = "Kafka-compatible endpoint for Event Hubs"
  value       = module.eventhubs.kafka_endpoint
  sensitive   = true
}

output "function_app_url" {
  description = "URL of the Function App"
  value       = module.functions.app_url
}

output "function_app_name" {
  description = "Name of the Function App"
  value       = module.functions.app_name
}

output "redis_hostname" {
  description = "Redis cache hostname"
  value       = azurerm_redis_cache.main.hostname
}

output "redis_ssl_port" {
  description = "Redis SSL port"
  value       = azurerm_redis_cache.main.ssl_port
}

output "application_insights_instrumentation_key" {
  description = "Application Insights instrumentation key"
  value       = azurerm_application_insights.main.instrumentation_key
  sensitive   = true
}

output "application_insights_connection_string" {
  description = "Application Insights connection string"
  value       = azurerm_application_insights.main.connection_string
  sensitive   = true
}

output "frontend_url" {
  description = "URL of the frontend Static Web App"
  value       = azurerm_static_site.frontend.default_host_name
}
