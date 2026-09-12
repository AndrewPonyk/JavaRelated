# RDS PostgreSQL module (stub). Primary + optional read replica.
# TODO: aws_db_instance (primary), aws_db_instance (replica),
# parameter group, subnet group, KMS encryption, automated backups.

variable "environment" { type = string }

variable "instance_class" {
  type    = string
  default = "db.t4g.medium"
}

variable "create_replica" {
  type    = bool
  default = false
}

output "endpoint" { value = "TODO" }
output "replica_endpoint" { value = "TODO" }
