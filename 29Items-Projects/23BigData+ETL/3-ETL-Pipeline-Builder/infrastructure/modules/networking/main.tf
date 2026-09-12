# Networking: VPC with private subnets for MSK / ECS / ElastiCache / MWAA.

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = merge(var.tags, { Name = "${var.name}-vpc" })
}

resource "aws_subnet" "private" {
  count             = length(var.private_subnet_cidrs)
  vpc_id            = aws_vpc.this.id
  cidr_block        = var.private_subnet_cidrs[count.index]
  availability_zone = var.azs[count.index]

  tags = merge(var.tags, { Name = "${var.name}-private-${var.azs[count.index]}" })
}

# Gateway endpoint so Glue/ECS reach S3 without NAT charges.
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.this.id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"

  tags = merge(var.tags, { Name = "${var.name}-s3-endpoint" })
}

# TODO(Phase 1):
#  - route tables + association of the S3 endpoint
#  - NAT gateway (or fully-private with interface endpoints for ECR, Logs,
#    Secrets Manager, STS, Glue, MonitoringSNS) — decide by egress needs/cost
