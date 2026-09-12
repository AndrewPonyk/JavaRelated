variable "name" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "subnet_ids" {
  type = list(string)
}

variable "client_cidr" {
  description = "CIDR allowed to reach Redis (usually the VPC CIDR)"
  type        = string
}

variable "redis_node_type" {
  type    = string
  default = "cache.t4g.micro"
}

variable "redis_replicas" {
  type    = number
  default = 0
}

variable "tags" {
  type    = map(string)
  default = {}
}
