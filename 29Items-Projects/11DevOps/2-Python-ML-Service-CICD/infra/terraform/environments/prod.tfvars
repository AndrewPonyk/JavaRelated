# Production environment — sized for live fraud-scoring traffic.
environment         = "prod"
location            = "westeurope"
node_count          = 5
node_vm_size        = "Standard_D8s_v5"
postgres_sku        = "GP_Standard_D4s_v3"
postgres_storage_mb = 131072

tags = {
  cost_center = "ml-platform"
  owner       = "fraud-team"
}
