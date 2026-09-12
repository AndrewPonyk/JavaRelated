# ---------------------------------------------------------------------------
# Azure ML workspace + required dependencies (storage account, workspace-based
# Application Insights). The workspace serves as the MLflow tracking server /
# model registry (registered model: fraud-detection, aliases champion /
# challenger) and runs the drift-triggered retraining pipelines.
# ---------------------------------------------------------------------------

resource "azurerm_storage_account" "aml" {
  name                     = var.storage_account_name
  resource_group_name      = var.resource_group_name
  location                 = var.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
  min_tls_version          = "TLS1_2"

  # NOTE: disable public network access + private endpoints for prod.

  tags = var.tags
}

# Application Insights must be workspace-based (classic is retired), so a
# Log Analytics workspace is created alongside it.
resource "azurerm_log_analytics_workspace" "aml" {
  name                = "${var.application_insights_name}-law"
  resource_group_name = var.resource_group_name
  location            = var.location
  sku                 = "PerGB2018"
  retention_in_days   = 30
  tags                = var.tags
}

resource "azurerm_application_insights" "aml" {
  name                = var.application_insights_name
  resource_group_name = var.resource_group_name
  location            = var.location
  workspace_id        = azurerm_log_analytics_workspace.aml.id
  application_type    = "web"
  tags                = var.tags
}

resource "azurerm_machine_learning_workspace" "main" {
  name                = var.workspace_name
  resource_group_name = var.resource_group_name
  location            = var.location

  application_insights_id = azurerm_application_insights.aml.id
  key_vault_id            = var.key_vault_id
  storage_account_id      = azurerm_storage_account.aml.id

  identity {
    type = "SystemAssigned"
  }

  public_network_access_enabled = true # NOTE: false + private endpoints for prod
  # NOTE: customer-managed keys (encryption block) if CMK is mandated.

  tags = var.tags
}

# NOTE: retraining compute cluster (created on demand by the drift-triggered
# retraining pipeline today; manage it here once sizing is settled):
#
# resource "azurerm_machine_learning_compute_cluster" "training" {
#   name                          = "cpu-retrain"
#   machine_learning_workspace_id = azurerm_machine_learning_workspace.main.id
#   location                      = var.location
#   vm_priority                   = "LowPriority"
#   vm_size                       = "Standard_DS3_v2"
#
#   scale_settings {
#     min_node_count                       = 0
#     max_node_count                       = 4
#     scale_down_nodes_after_idle_duration = "PT5M"
#   }
#
#   identity {
#     type = "SystemAssigned"
#   }
# }
