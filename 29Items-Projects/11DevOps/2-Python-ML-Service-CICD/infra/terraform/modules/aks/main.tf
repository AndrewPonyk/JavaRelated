# ---------------------------------------------------------------------------
# AKS cluster hosting the fraud-detection namespace (blue-green Deployments
# fraud-api-blue / fraud-api-green behind the fraud-api Service).
# ---------------------------------------------------------------------------

resource "azurerm_kubernetes_cluster" "main" {
  name                = var.name
  resource_group_name = var.resource_group_name
  location            = var.location
  dns_prefix          = var.dns_prefix
  kubernetes_version  = var.kubernetes_version

  default_node_pool {
    name       = "system"
    node_count = var.node_count
    vm_size    = var.node_vm_size

    # NOTE: enable autoscaling (auto_scaling_enabled + min_count/max_count)
    # and a dedicated user node pool for the API workload in prod.
  }

  identity {
    type = "SystemAssigned"
  }

  # Workload identity: lets fraud-api pods federate to Azure ML / Key Vault
  # without node-level credentials.
  oidc_issuer_enabled       = true
  workload_identity_enabled = true

  # NOTE: monitoring addon — oms_agent { log_analytics_workspace_id = ... }
  #       (Container Insights) plus Prometheus-managed addon if desired.
  # NOTE: network_profile (Azure CNI overlay), private cluster, and
  #       azure_policy_enabled hardening for prod.

  tags = var.tags
}

# Allow the cluster's kubelet identity to pull fraud-detection-api images
# from ACR without imagePullSecrets.
resource "azurerm_role_assignment" "acr_pull" {
  scope                            = var.acr_id
  role_definition_name             = "AcrPull"
  principal_id                     = azurerm_kubernetes_cluster.main.kubelet_identity[0].object_id
  skip_service_principal_aad_check = true
}
