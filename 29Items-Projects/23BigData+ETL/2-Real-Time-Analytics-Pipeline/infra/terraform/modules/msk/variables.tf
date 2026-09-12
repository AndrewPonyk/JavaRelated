variable "cluster_name" {
  type = string
}

variable "kafka_version" {
  type    = string
  default = "3.7.x" # MSK's evergreen 3.7 line — TODO: verify latest supported
}

variable "broker_count" {
  type        = number
  default     = 3
  description = "Must be a multiple of the AZ count"
}

variable "instance_type" {
  type    = string
  default = "kafka.m7g.large"
}

variable "ebs_volume_size_gb" {
  type    = number
  default = 100
}

variable "subnet_ids" {
  type        = list(string)
  description = "Private subnets, one per AZ"
}

variable "security_group_ids" {
  type = list(string)
}

variable "tags" {
  type    = map(string)
  default = {}
}
