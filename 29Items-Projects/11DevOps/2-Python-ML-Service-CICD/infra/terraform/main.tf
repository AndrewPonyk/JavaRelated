# ---------------------------------------------------------------------------
# Azure footprint for the Fraud Detection API:
#   resource group + ACR + AKS + PostgreSQL Flexible Server + Key Vault
#   + Azure ML workspace (MLflow tracking / automated retraining target).
# ---------------------------------------------------------------------------

locals {
  # rg-fraud-detection-<env>
  resource_group_name = "rg-${var.project_name}-${var.environment}"

  aks_name           = "fraud-aks-${var.environment}"
  postgres_name      = "psql-fraud-${var.environment}"
  key_vault_name     = "kv-fraud-${var.environment}"
  aml_workspace_name = "mlw-fraud-${var.environment}"

  # ACR names are alphanumeric-only and globally unique. Per project
  # convention the registry is `frauddetectacr` (images are pushed to
  # frauddetectacr.azurecr.io/fraud-detection-api:<tag>).
  # NOTE: in practice a single registry is shared by all environments —
  # deploy it once (e.g. from a shared/prod state) instead of per env.
  acr_name = "frauddetectacr"

  # Storage account names: lowercase alphanumeric, <= 24 chars, globally unique.
  aml_storage_account_name = "stfraudml${var.environment}"

  tags = merge(
    var.tags,
    {
      project     = var.project_name
      environment = var.environment
      managed_by  = "terraform"
    },
  )
}

resource "azurerm_resource_group" "main" {
  name     = local.resource_group_name
  location = var.location
  tags     = local.tags
}

# ---------------------------------------------------------------------------
# Container registry
# ---------------------------------------------------------------------------

module "acr" {
  source = "./modules/acr"

  name                = local.acr_name
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  tags                = local.tags
}

# ---------------------------------------------------------------------------
# Kubernetes cluster (blue-green deployments of fraud-api run here)
# ---------------------------------------------------------------------------

module "aks" {
  source = "./modules/aks"

  name                = local.aks_name
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  dns_prefix          = "fraud-${var.environment}"
  node_count          = var.node_count
  node_vm_size        = var.node_vm_size
  acr_id              = module.acr.acr_id
  tags                = local.tags
}

# ---------------------------------------------------------------------------
# PostgreSQL (predictions / ab_assignments / drift_reports tables)
# ---------------------------------------------------------------------------

resource "random_password" "postgres_admin" {
  length  = 24
  special = false # keep the FRAUD_DATABASE_URL connection string URL-safe
}

module "postgres" {
  source = "./modules/postgres"

  name                   = local.postgres_name
  resource_group_name    = azurerm_resource_group.main.name
  location               = azurerm_resource_group.main.location
  sku_name               = var.postgres_sku
  storage_mb             = var.postgres_storage_mb
  administrator_login    = "fraudadmin"
  administrator_password = random_password.postgres_admin.result
  tags                   = local.tags
}

# ---------------------------------------------------------------------------
# Key Vault (source for the fraud-api-secrets K8s secret material)
# ---------------------------------------------------------------------------

module "keyvault" {
  source = "./modules/keyvault"

  name                     = local.key_vault_name
  resource_group_name      = azurerm_resource_group.main.name
  location                 = azurerm_resource_group.main.location
  purge_protection_enabled = var.environment == "prod"
  tags                     = local.tags
}

# NOTE: persist the generated Postgres password into Key Vault
# (azurerm_key_vault_secret "postgres-admin-password") once the deploying
# principal holds the "Key Vault Secrets Officer" RBAC role on the vault.

# ---------------------------------------------------------------------------
# Azure ML workspace (model registry / drift-triggered retraining jobs)
# ---------------------------------------------------------------------------

module "azureml" {
  source = "./modules/azureml"

  workspace_name            = local.aml_workspace_name
  resource_group_name       = azurerm_resource_group.main.name
  location                  = azurerm_resource_group.main.location
  key_vault_id              = module.keyvault.key_vault_id
  storage_account_name      = local.aml_storage_account_name
  application_insights_name = "appi-fraud-${var.environment}"
  tags                      = local.tags
}
