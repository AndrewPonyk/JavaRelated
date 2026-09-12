# ---------------------------------------------------------------------------
# Azure Container Registry for fraud-detection-api images.
# ---------------------------------------------------------------------------

resource "azurerm_container_registry" "main" {
  name                = var.name
  resource_group_name = var.resource_group_name
  location            = var.location
  sku                 = var.sku

  # Admin user disabled by design: AKS pulls via AcrPull role assignment on
  # its kubelet identity, CI pushes via the Azure DevOps service connection.
  admin_enabled = false

  # NOTE: private endpoint + disable public network access (Premium SKU only).
  # NOTE: retention_policy / trust_policy hardening for prod.

  tags = var.tags
}
