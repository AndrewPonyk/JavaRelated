# ---------------------------------------------------------------------------
# Remote state: Azure Storage backend.
#
# The values below are PLACEHOLDERS. The real backend configuration is
# injected per environment at init time via -backend-config, e.g.:
#
#   terraform init `
#     -backend-config="resource_group_name=rg-terraform-state" `
#     -backend-config="storage_account_name=stfraudtfstate" `
#     -backend-config="container_name=tfstate" `
#     -backend-config="key=fraud-detection.dev.tfstate"
#
# Use a distinct `key` per environment (fraud-detection.<env>.tfstate for
# dev / staging / prod) so state files never collide. The Azure DevOps
# pipeline passes these values from pipeline variables per stage.
# ---------------------------------------------------------------------------

terraform {
  backend "azurerm" {
    resource_group_name  = "rg-terraform-state"          # placeholder
    storage_account_name = "stfraudtfstate"              # placeholder (globally unique)
    container_name       = "tfstate"                     # placeholder
    key                  = "fraud-detection.dev.tfstate" # placeholder; override per env
  }
}
