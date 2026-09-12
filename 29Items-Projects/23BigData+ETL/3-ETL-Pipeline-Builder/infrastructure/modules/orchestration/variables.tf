variable "name" {
  type = string
}

variable "enabled" {
  description = "Create the MWAA environment (dev: false — compose Airflow instead)"
  type        = bool
  default     = false
}

variable "environment_class" {
  type    = string
  default = "mw1.small"
}

variable "subnet_ids" {
  type = list(string)
}

variable "security_group_ids" {
  type    = list(string)
  default = []
}

variable "tags" {
  type    = map(string)
  default = {}
}
