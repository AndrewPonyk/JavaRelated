# Staging environment — prod-like sizing for realistic load / A-B rehearsal.
environment         = "staging"
location            = "westeurope"
node_count          = 3
node_vm_size        = "Standard_D4s_v5"
postgres_sku        = "GP_Standard_D2s_v3"
postgres_storage_mb = 65536

tags = {
  cost_center = "ml-platform"
  owner       = "fraud-team"
}
