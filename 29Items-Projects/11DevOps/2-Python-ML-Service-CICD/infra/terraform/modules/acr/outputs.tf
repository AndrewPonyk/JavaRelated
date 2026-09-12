output "acr_id" {
  description = "Resource ID of the container registry (scope for AcrPull role assignments)."
  value       = azurerm_container_registry.main.id
}

output "login_server" {
  description = "Registry login server, e.g. frauddetectacr.azurecr.io."
  value       = azurerm_container_registry.main.login_server
}

output "name" {
  description = "Registry name."
  value       = azurerm_container_registry.main.name
}
