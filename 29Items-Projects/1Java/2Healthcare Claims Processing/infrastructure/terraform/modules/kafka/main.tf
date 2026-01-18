# Event Hubs (Kafka-compatible) Module

variable "resource_group_name" {
  type = string
}

variable "location" {
  type = string
}

variable "environment" {
  type = string
}

variable "sku" {
  type    = string
  default = "Standard"
}

variable "capacity" {
  type    = number
  default = 1
}

variable "tags" {
  type    = map(string)
  default = {}
}

# Event Hubs Namespace
resource "azurerm_eventhub_namespace" "main" {
  name                     = "claims-eventhubs-${var.environment}"
  location                 = var.location
  resource_group_name      = var.resource_group_name
  sku                      = var.sku
  capacity                 = var.capacity
  auto_inflate_enabled     = var.environment == "prod"
  maximum_throughput_units = var.environment == "prod" ? 10 : 0

  tags = var.tags
}

# Event Hub for Claim Events
resource "azurerm_eventhub" "claim_events" {
  name                = "claim-events"
  namespace_name      = azurerm_eventhub_namespace.main.name
  resource_group_name = var.resource_group_name
  partition_count     = var.environment == "prod" ? 8 : 2
  message_retention   = var.environment == "prod" ? 7 : 1
}

# Event Hub for Fraud Alerts
resource "azurerm_eventhub" "fraud_alerts" {
  name                = "fraud-alerts"
  namespace_name      = azurerm_eventhub_namespace.main.name
  resource_group_name = var.resource_group_name
  partition_count     = var.environment == "prod" ? 4 : 2
  message_retention   = var.environment == "prod" ? 7 : 1
}

# Consumer Groups
resource "azurerm_eventhub_consumer_group" "claims_processor" {
  name                = "claims-processor"
  namespace_name      = azurerm_eventhub_namespace.main.name
  eventhub_name       = azurerm_eventhub.claim_events.name
  resource_group_name = var.resource_group_name
}

resource "azurerm_eventhub_consumer_group" "fraud_handler" {
  name                = "fraud-handler"
  namespace_name      = azurerm_eventhub_namespace.main.name
  eventhub_name       = azurerm_eventhub.fraud_alerts.name
  resource_group_name = var.resource_group_name
}

resource "azurerm_eventhub_consumer_group" "indexer" {
  name                = "elasticsearch-indexer"
  namespace_name      = azurerm_eventhub_namespace.main.name
  eventhub_name       = azurerm_eventhub.claim_events.name
  resource_group_name = var.resource_group_name
}

# Authorization Rule for applications
resource "azurerm_eventhub_namespace_authorization_rule" "app" {
  name                = "claims-app"
  namespace_name      = azurerm_eventhub_namespace.main.name
  resource_group_name = var.resource_group_name

  listen = true
  send   = true
  manage = false
}

# Outputs
output "namespace_name" {
  value = azurerm_eventhub_namespace.main.name
}

output "kafka_endpoint" {
  value = "${azurerm_eventhub_namespace.main.name}.servicebus.windows.net:9093"
}

output "connection_string" {
  value     = azurerm_eventhub_namespace.main.default_primary_connection_string
  sensitive = true
}
