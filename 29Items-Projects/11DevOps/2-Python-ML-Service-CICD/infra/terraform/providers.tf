# ---------------------------------------------------------------------------
# Terraform & provider requirements for the Fraud Detection API platform.
# ---------------------------------------------------------------------------

terraform {
  required_version = ">= 1.7"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

provider "azurerm" {
  features {}

  # Subscription / tenant credentials come from ARM_* environment variables in
  # CI (Azure DevOps service connection) or from `az login` locally.
}
