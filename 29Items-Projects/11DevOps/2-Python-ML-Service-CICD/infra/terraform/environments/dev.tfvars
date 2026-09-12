# Dev environment — smallest footprint, burstable database.
environment         = "dev"
location            = "westeurope"
node_count          = 2
node_vm_size        = "Standard_D2s_v5"
postgres_sku        = "B_Standard_B1ms"
postgres_storage_mb = 32768

tags = {
  cost_center = "ml-platform"
  owner       = "fraud-team"
}
