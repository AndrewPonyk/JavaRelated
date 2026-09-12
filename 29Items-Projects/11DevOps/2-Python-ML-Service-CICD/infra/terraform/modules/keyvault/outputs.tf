output "key_vault_id" {
  description = "Resource ID of the Key Vault (wired into the Azure ML workspace)."
  value       = azurerm_key_vault.main.id
}

output "key_vault_uri" {
  description = "Vault URI, e.g. https://kv-fraud-dev.vault.azure.net/."
  value       = azurerm_key_vault.main.vault_uri
}

output "name" {
  description = "Key Vault name."
  value       = azurerm_key_vault.main.name
}
