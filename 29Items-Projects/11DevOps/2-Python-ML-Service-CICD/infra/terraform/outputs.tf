# ---------------------------------------------------------------------------
# Root outputs consumed by the Azure DevOps deploy stages.
# ---------------------------------------------------------------------------

output "resource_group_name" {
  description = "Resource group containing the environment footprint."
  value       = azurerm_resource_group.main.name
}

output "aks_cluster_name" {
  description = "AKS cluster name (use with resource_group_name for kubeconfig)."
  value       = module.aks.aks_name
}

output "kubeconfig_command" {
  description = "Command to fetch kubeconfig for kubectl / blue-green deploy scripts."
  value       = "az aks get-credentials --resource-group ${azurerm_resource_group.main.name} --name ${module.aks.aks_name}"
}

output "acr_login_server" {
  description = "ACR login server (docker push target, e.g. frauddetectacr.azurecr.io)."
  value       = module.acr.login_server
}

output "postgres_fqdn" {
  description = "PostgreSQL Flexible Server FQDN (host part of FRAUD_DATABASE_URL)."
  value       = module.postgres.fqdn
}

output "azureml_workspace_name" {
  description = "Azure ML workspace name (MLflow tracking / registry backend and retraining target)."
  value       = module.azureml.workspace_name
}

output "key_vault_uri" {
  description = "Key Vault URI backing the fraud-api-secrets material."
  value       = module.keyvault.key_vault_uri
}
