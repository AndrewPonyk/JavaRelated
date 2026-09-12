output "workspace_id" {
  description = "Resource ID of the Azure ML workspace."
  value       = azurerm_machine_learning_workspace.main.id
}

output "workspace_name" {
  description = "Azure ML workspace name (MLflow tracking backend)."
  value       = azurerm_machine_learning_workspace.main.name
}

output "workspace_principal_id" {
  description = "Object ID of the workspace's system-assigned identity."
  value       = azurerm_machine_learning_workspace.main.identity[0].principal_id
}

output "storage_account_id" {
  description = "Resource ID of the workspace's default storage account."
  value       = azurerm_storage_account.aml.id
}

output "application_insights_id" {
  description = "Resource ID of the workspace's Application Insights component."
  value       = azurerm_application_insights.aml.id
}
