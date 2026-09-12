variable "name" {
  type        = string
  description = "AKS cluster name, e.g. fraud-aks-dev."
}

variable "resource_group_name" {
  type        = string
  description = "Resource group in which to create the cluster."
}

variable "location" {
  type        = string
  description = "Azure region."
}

variable "dns_prefix" {
  type        = string
  description = "DNS prefix for the cluster API server FQDN."
}

variable "kubernetes_version" {
  type        = string
  description = "Kubernetes version. Null lets AKS pick the current default."
  default     = null
}

variable "node_count" {
  type        = number
  description = "Node count for the default (system) node pool."
}

variable "node_vm_size" {
  type        = string
  description = "VM size for the default node pool, e.g. Standard_D2s_v5."
}

variable "acr_id" {
  type        = string
  description = "Resource ID of the container registry the kubelet identity must pull from."
}

variable "tags" {
  type        = map(string)
  description = "Tags applied to the cluster."
  default     = {}
}
