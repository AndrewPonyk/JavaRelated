variable "project" {
  type    = string
  default = "etl-pipeline-builder"
}

variable "env" {
  type    = string
  default = "dev"
}

variable "region" {
  type    = string
  default = "eu-central-1"
}

variable "vpc_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

variable "azs" {
  type    = list(string)
  default = ["eu-central-1a", "eu-central-1b"]
}

variable "private_subnet_cidrs" {
  type    = list(string)
  default = ["10.20.1.0/24", "10.20.2.0/24"]
}

variable "msk_broker_count" {
  type    = number
  default = 2
}

variable "msk_instance_type" {
  type    = string
  default = "kafka.t3.small"
}

variable "msk_ebs_gb" {
  type    = number
  default = 100
}

variable "mwaa_enabled" {
  type    = bool
  default = false
}
