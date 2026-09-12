variable "name" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "subnet_ids" {
  description = "Private subnets; count must be a multiple of the AZ count"
  type        = list(string)
}

variable "client_cidr" {
  description = "CIDR allowed to reach the brokers (usually the VPC CIDR)"
  type        = string
}

variable "kafka_version" {
  type    = string
  default = "3.6.0"
}

variable "broker_count" {
  type    = number
  default = 2
}

variable "instance_type" {
  type    = string
  default = "kafka.t3.small"
}

variable "ebs_volume_size_gb" {
  type    = number
  default = 100
}

variable "tags" {
  type    = map(string)
  default = {}
}
