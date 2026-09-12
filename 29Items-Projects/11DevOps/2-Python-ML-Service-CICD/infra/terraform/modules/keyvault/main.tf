# ---------------------------------------------------------------------------
# Key Vault: source of secret material for the fraud-api-secrets K8s Secret
# (FRAUD_DATABASE_URL, MLflow credentials, ...) and the Azure ML workspace.
# ---------------------------------------------------------------------------

data "azurerm_client_config" "current" {}

resource "azurerm_key_vault" "main" {
  name                = var.name
  resource_group_name = var.resource_group_name
  location            = var.location
  tenant_id           = data.azurerm_client_config.current.tenant_id
  sku_name            = var.sku_name

  # RBAC data-plane authorization instead of legacy access policies.
  rbac_authorization_enabled = true

  soft_delete_retention_days = 7
  purge_protection_enabled   = var.purge_protection_enabled

  # NOTE: RBAC role assignments (data plane) — grant:
  #   - "Key Vault Secrets User"    to the fraud-api workload identity,
  #   - "Key Vault Secrets Officer" to the CI service connection,
  #   - "Key Vault Administrator"   to the platform team group.
  # NOTE: network_acls (default Deny + AKS subnet) and private endpoint.
  # NOTE: CMK / key management if customer-managed encryption is required.

  tags = var.tags
}
