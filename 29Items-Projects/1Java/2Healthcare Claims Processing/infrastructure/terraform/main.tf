# Healthcare Claims Processing - Terraform Main Configuration

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.80"
    }
    azuread = {
      source  = "hashicorp/azuread"
      version = "~> 2.45"
    }
  }

  backend "azurerm" {
    # Backend config provided via CLI or environment
  }
}

provider "azurerm" {
  features {
    key_vault {
      purge_soft_delete_on_destroy = false
    }
  }
}

# Resource Group
resource "azurerm_resource_group" "main" {
  name     = "claims-rg-${var.environment}"
  location = var.location

  tags = local.common_tags
}

# Virtual Network
resource "azurerm_virtual_network" "main" {
  name                = "claims-vnet-${var.environment}"
  address_space       = ["10.0.0.0/16"]
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name

  tags = local.common_tags
}

# Subnets
resource "azurerm_subnet" "app" {
  name                 = "app-subnet"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = ["10.0.1.0/24"]

  delegation {
    name = "functions-delegation"
    service_delegation {
      name    = "Microsoft.Web/serverFarms"
      actions = ["Microsoft.Network/virtualNetworks/subnets/action"]
    }
  }
}

resource "azurerm_subnet" "data" {
  name                 = "data-subnet"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = ["10.0.2.0/24"]

  service_endpoints = [
    "Microsoft.Sql",
    "Microsoft.Storage"
  ]
}

# Key Vault
resource "azurerm_key_vault" "main" {
  name                = "claims-kv-${var.environment}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  tenant_id           = data.azurerm_client_config.current.tenant_id
  sku_name            = "standard"

  purge_protection_enabled   = var.environment == "prod"
  soft_delete_retention_days = 7

  tags = local.common_tags
}

# PostgreSQL Flexible Server
module "postgresql" {
  source = "./modules/postgresql"

  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  environment         = var.environment
  subnet_id           = azurerm_subnet.data.id
  key_vault_id        = azurerm_key_vault.main.id

  sku_name       = var.db_sku_name
  storage_mb     = var.db_storage_mb
  admin_username = "claimsadmin"

  tags = local.common_tags
}

# Event Hubs (Kafka compatible)
module "eventhubs" {
  source = "./modules/kafka"

  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  environment         = var.environment

  sku      = var.eventhubs_sku
  capacity = var.eventhubs_capacity

  tags = local.common_tags
}

# Azure Functions
module "functions" {
  source = "./modules/azure-functions"

  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  environment         = var.environment
  subnet_id           = azurerm_subnet.app.id
  key_vault_id        = azurerm_key_vault.main.id

  app_service_plan_sku = var.function_sku

  app_settings = {
    DB_HOST                   = module.postgresql.server_fqdn
    DB_NAME                   = module.postgresql.database_name
    KAFKA_BOOTSTRAP_SERVERS   = module.eventhubs.kafka_endpoint
    ES_HOSTS                  = module.elasticsearch.endpoint
    APPLICATIONINSIGHTS_CONNECTION_STRING = azurerm_application_insights.main.connection_string
  }

  tags = local.common_tags
}

# Elasticsearch (Azure Cognitive Search as alternative)
module "elasticsearch" {
  source = "./modules/elasticsearch"

  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  environment         = var.environment

  sku = var.search_sku

  tags = local.common_tags
}

# Redis Cache
resource "azurerm_redis_cache" "main" {
  name                = "claims-redis-${var.environment}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  capacity            = var.redis_capacity
  family              = var.redis_family
  sku_name            = var.redis_sku
  enable_non_ssl_port = false
  minimum_tls_version = "1.2"

  redis_configuration {
    maxmemory_policy = "volatile-lru"
  }

  tags = local.common_tags
}

# Application Insights
resource "azurerm_application_insights" "main" {
  name                = "claims-insights-${var.environment}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  application_type    = "java"

  tags = local.common_tags
}

# Static Web App for Frontend
resource "azurerm_static_site" "frontend" {
  name                = "claims-frontend-${var.environment}"
  resource_group_name = azurerm_resource_group.main.name
  location            = "westus2" # Static Web Apps has limited regions
  sku_tier            = var.environment == "prod" ? "Standard" : "Free"
  sku_size            = var.environment == "prod" ? "Standard" : "Free"

  tags = local.common_tags
}

# Data sources
data "azurerm_client_config" "current" {}

# Local values
locals {
  common_tags = {
    Environment = var.environment
    Project     = "healthcare-claims"
    ManagedBy   = "terraform"
  }
}
