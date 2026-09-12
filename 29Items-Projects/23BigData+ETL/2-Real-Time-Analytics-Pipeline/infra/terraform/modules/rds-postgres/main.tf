# RDS PostgreSQL — durable aggregates + alert workflow. Multi-AZ in prod, single in dev.

variable "identifier" { type = string }
variable "engine_version" {
  type    = string
  default = "16.4"
}
variable "instance_class" {
  type    = string
  default = "db.t4g.medium"
}
variable "allocated_storage_gb" {
  type    = number
  default = 50
}
variable "multi_az" {
  type    = bool
  default = false
}
variable "db_name" {
  type    = string
  default = "analytics"
}
variable "username" {
  type    = string
  default = "analytics"
}
variable "subnet_ids" { type = list(string) }
variable "security_group_ids" { type = list(string) }
variable "tags" {
  type    = map(string)
  default = {}
}

resource "aws_db_subnet_group" "this" {
  name       = "${var.identifier}-subnets"
  subnet_ids = var.subnet_ids
  tags       = var.tags
}

resource "aws_db_instance" "this" {
  identifier        = var.identifier
  engine            = "postgres"
  engine_version    = var.engine_version
  instance_class    = var.instance_class
  allocated_storage = var.allocated_storage_gb
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = var.db_name
  username = var.username
  # Master password lives in Secrets Manager, never in state/tfvars.
  manage_master_user_password = true

  multi_az               = var.multi_az
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = var.security_group_ids
  publicly_accessible    = false

  backup_retention_period = 7
  deletion_protection     = var.multi_az # prod-ish envs get protection
  skip_final_snapshot     = !var.multi_az

  # NOTE: XA / prepared transactions stay disabled — the pipeline uses idempotent
  # upserts, not two-phase commit (docs/TECH-NOTES.md §3.6.10).

  tags = var.tags
}

output "endpoint" { value = aws_db_instance.this.address }
output "master_user_secret_arn" { value = aws_db_instance.this.master_user_secret[0].secret_arn }
