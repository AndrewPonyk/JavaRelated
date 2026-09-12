# ---------------------------------------------------------------------------
# PostgreSQL Flexible Server backing the fraud-detection API
# (predictions / model_versions / ab_assignments / drift_reports tables,
# managed by Alembic from the application side).
# ---------------------------------------------------------------------------

resource "azurerm_postgresql_flexible_server" "main" {
  name                = var.name
  resource_group_name = var.resource_group_name
  location            = var.location

  version                = var.postgres_version
  administrator_login    = var.administrator_login
  administrator_password = var.administrator_password

  sku_name   = var.sku_name
  storage_mb = var.storage_mb
  zone       = "1"

  # NOTE: high_availability { mode = "ZoneRedundant" } for prod.
  # NOTE: backup_retention_days / geo_redundant_backup_enabled per env.
  # NOTE: private access (delegated subnet + private DNS zone) instead of
  #       public endpoint; then drop the firewall rules below entirely.

  tags = var.tags
}

resource "azurerm_postgresql_flexible_server_database" "fraud" {
  name      = var.database_name
  server_id = azurerm_postgresql_flexible_server.main.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

# NOTE: firewall rules — at minimum allow the AKS egress IPs, e.g.:
# resource "azurerm_postgresql_flexible_server_firewall_rule" "aks" {
#   name             = "allow-aks-egress"
#   server_id        = azurerm_postgresql_flexible_server.main.id
#   start_ip_address = "<aks-egress-ip>"
#   end_ip_address   = "<aks-egress-ip>"
# }
