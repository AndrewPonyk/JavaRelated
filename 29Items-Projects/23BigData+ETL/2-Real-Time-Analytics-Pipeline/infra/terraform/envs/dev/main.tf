# DEV environment — full topology at minimal sizing.
# Staging/prod mirror this file with sizing/flag overrides in their tfvars.

terraform {
  required_version = ">= 1.9"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.70"
    }
  }
}

provider "aws" {
  region = var.region
  default_tags {
    tags = local.tags
  }
}

locals {
  name_prefix = "rtap-${var.environment}"
  tags = {
    Project     = "rtap"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

# ── Artifact bucket (Flink jars keyed by git SHA — cd-deploy.yml) ────────────
resource "aws_s3_bucket" "artifacts" {
  bucket = "${local.name_prefix}-artifacts" # TODO: add account-id suffix for global uniqueness
}

resource "aws_s3_bucket_versioning" "artifacts" {
  bucket = aws_s3_bucket.artifacts.id
  versioning_configuration {
    status = "Enabled"
  }
}

# ── Network ──────────────────────────────────────────────────────────────────
module "networking" {
  source      = "../../modules/networking"
  name_prefix = local.name_prefix
  azs         = var.azs
  tags        = local.tags
}

# Pairwise SG rules: clients → brokers on 9098 (IAM SASL), nothing else.
resource "aws_security_group" "kafka_clients" {
  name   = "${local.name_prefix}-kafka-clients"
  vpc_id = module.networking.vpc_id
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = local.tags
}

resource "aws_security_group" "msk_brokers" {
  name   = "${local.name_prefix}-msk-brokers"
  vpc_id = module.networking.vpc_id
  ingress {
    description     = "Kafka IAM SASL from pipeline clients"
    from_port       = 9098
    to_port         = 9098
    protocol        = "tcp"
    security_groups = [aws_security_group.kafka_clients.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = local.tags
}

resource "aws_security_group" "data_stores" {
  name   = "${local.name_prefix}-data-stores"
  vpc_id = module.networking.vpc_id
  ingress {
    description     = "HTTPS (OpenSearch) from pipeline clients"
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    security_groups = [aws_security_group.kafka_clients.id]
  }
  ingress {
    description     = "PostgreSQL from pipeline clients"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.kafka_clients.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = local.tags
}

# ── Kafka (MSK) ──────────────────────────────────────────────────────────────
module "msk" {
  source             = "../../modules/msk"
  cluster_name       = "${local.name_prefix}-kafka"
  broker_count       = 3
  instance_type      = var.msk_instance_type
  subnet_ids         = module.networking.private_subnet_ids
  security_group_ids = [aws_security_group.msk_brokers.id]
  tags               = local.tags
}

# ── Flink jobs (Amazon Managed Flink) ────────────────────────────────────────
module "flink_aggregation" {
  source               = "../../modules/flink-app"
  name                 = "${local.name_prefix}-aggregation"
  artifacts_bucket_arn = aws_s3_bucket.artifacts.arn
  jar_s3_key           = var.aggregation_jar_key # updated by CD per deploy
  parallelism          = 4
  runtime_properties = {
    "kafka.bootstrap.servers" = module.msk.bootstrap_brokers_sasl_iam
  }
  subnet_ids         = module.networking.private_subnet_ids
  security_group_ids = [aws_security_group.kafka_clients.id]
  tags               = local.tags
}

module "flink_anomaly" {
  source               = "../../modules/flink-app"
  name                 = "${local.name_prefix}-anomaly"
  artifacts_bucket_arn = aws_s3_bucket.artifacts.arn
  jar_s3_key           = var.anomaly_jar_key
  parallelism          = 2
  runtime_properties = {
    "kafka.bootstrap.servers" = module.msk.bootstrap_brokers_sasl_iam
  }
  subnet_ids         = module.networking.private_subnet_ids
  security_group_ids = [aws_security_group.kafka_clients.id]
  tags               = local.tags
}

# ── Stores ───────────────────────────────────────────────────────────────────
module "opensearch" {
  source             = "../../modules/opensearch"
  domain_name        = "${local.name_prefix}-search"
  instance_count     = 3
  subnet_ids         = module.networking.private_subnet_ids
  security_group_ids = [aws_security_group.data_stores.id]
  tags               = local.tags
}

module "postgres" {
  source             = "../../modules/rds-postgres"
  identifier         = "${local.name_prefix}-pg"
  multi_az           = false # true in prod tfvars
  subnet_ids         = module.networking.private_subnet_ids
  security_group_ids = [aws_security_group.data_stores.id]
  tags               = local.tags
}

# ── Serving ──────────────────────────────────────────────────────────────────
module "ecs_api" {
  source = "../../modules/ecs-api"
  name   = "${local.name_prefix}-api"
  image  = var.api_image # ECR URI:SHA, set by CD
  environment = {
    KAFKA_BOOTSTRAP_SERVERS = module.msk.bootstrap_brokers_sasl_iam
    POSTGRES_HOST           = module.postgres.endpoint
    ELASTICSEARCH_URL       = "https://${module.opensearch.endpoint}"
  }
  tags = local.tags
}

module "grafana" {
  source = "../../modules/grafana"
  name   = "${local.name_prefix}-grafana"
  tags   = local.tags
}

# TODO(phase-2): Cognito user pool (API/SPA auth) · S3+CloudFront for the SPA ·
# ECR repository · SSM parameters (/rtap/dev/…) · MSK topics via a kafka provider step.

output "kafka_bootstrap" { value = module.msk.bootstrap_brokers_sasl_iam }
output "opensearch_endpoint" { value = module.opensearch.endpoint }
output "postgres_endpoint" { value = module.postgres.endpoint }
output "grafana_endpoint" { value = module.grafana.workspace_endpoint }
