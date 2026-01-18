# Azure Functions Module

variable "resource_group_name" {
  type = string
}

variable "location" {
  type = string
}

variable "environment" {
  type = string
}

variable "subnet_id" {
  type = string
}

variable "key_vault_id" {
  type = string
}

variable "app_service_plan_sku" {
  type    = string
  default = "Y1"
}

variable "app_settings" {
  type    = map(string)
  default = {}
}

variable "tags" {
  type    = map(string)
  default = {}
}

# Storage Account for Functions
resource "azurerm_storage_account" "functions" {
  name                     = "claimsfunc${var.environment}${random_string.storage_suffix.result}"
  resource_group_name      = var.resource_group_name
  location                 = var.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
  min_tls_version          = "TLS1_2"

  tags = var.tags
}

resource "random_string" "storage_suffix" {
  length  = 6
  special = false
  upper   = false
}

# App Service Plan
resource "azurerm_service_plan" "functions" {
  name                = "claims-plan-${var.environment}"
  resource_group_name = var.resource_group_name
  location            = var.location
  os_type             = "Linux"
  sku_name            = var.app_service_plan_sku

  tags = var.tags
}

# Function App
resource "azurerm_linux_function_app" "main" {
  name                = "claims-processing-${var.environment}"
  resource_group_name = var.resource_group_name
  location            = var.location

  storage_account_name       = azurerm_storage_account.functions.name
  storage_account_access_key = azurerm_storage_account.functions.primary_access_key
  service_plan_id            = azurerm_service_plan.functions.id

  site_config {
    always_on = var.app_service_plan_sku != "Y1"

    application_stack {
      java_version = "21"
    }

    cors {
      allowed_origins = ["*"]
    }
  }

  app_settings = merge(var.app_settings, {
    FUNCTIONS_WORKER_RUNTIME       = "custom"
    WEBSITE_RUN_FROM_PACKAGE       = "1"
    SCM_DO_BUILD_DURING_DEPLOYMENT = "false"
  })

  identity {
    type = "SystemAssigned"
  }

  tags = var.tags
}

# Staging Slot (for blue-green deployments)
resource "azurerm_linux_function_app_slot" "staging" {
  count           = var.environment == "prod" ? 1 : 0
  name            = "staging"
  function_app_id = azurerm_linux_function_app.main.id

  storage_account_name       = azurerm_storage_account.functions.name
  storage_account_access_key = azurerm_storage_account.functions.primary_access_key

  site_config {
    always_on = true

    application_stack {
      java_version = "21"
    }
  }

  app_settings = azurerm_linux_function_app.main.app_settings

  identity {
    type = "SystemAssigned"
  }

  tags = var.tags
}

# Key Vault Access
resource "azurerm_key_vault_access_policy" "functions" {
  key_vault_id = var.key_vault_id
  tenant_id    = azurerm_linux_function_app.main.identity[0].tenant_id
  object_id    = azurerm_linux_function_app.main.identity[0].principal_id

  secret_permissions = [
    "Get",
    "List"
  ]
}

# Outputs
output "app_url" {
  value = "https://${azurerm_linux_function_app.main.default_hostname}"
}

output "app_name" {
  value = azurerm_linux_function_app.main.name
}

output "principal_id" {
  value = azurerm_linux_function_app.main.identity[0].principal_id
}
