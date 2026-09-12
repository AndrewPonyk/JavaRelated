# Root Terraform for the EHR Integration Platform (AWS, HIPAA-eligible services).
# Skeleton only — wire real modules in P1-7. Manage state in S3 + DynamoDB lock.

terraform {
  required_version = ">= 1.7"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
  }

  # TODO(P1-7): uncomment and configure remote state (encrypted, locked).
  # backend "s3" {
  #   bucket         = "ehr-platform-tfstate"
  #   key            = "platform/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "ehr-platform-tflock"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region
  default_tags {
    tags = {
      Project     = "ehr-integration-platform"
      Environment = var.environment
      Compliance  = "HIPAA"
      ManagedBy   = "terraform"
    }
  }
}

# --- Network -----------------------------------------------------------------
# TODO(P1-7): VPC with private subnets only for PHI workloads, flow logs on.
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.0"

  name = "ehr-${var.environment}"
  cidr = var.vpc_cidr
  # azs, private_subnets, public_subnets, nat, flow logs ... (P1-7)
}

# --- KMS ---------------------------------------------------------------------
# Customer-managed key for encrypting PHI at rest (EBS, RDS, MSK, S3).
resource "aws_kms_key" "phi" {
  description             = "CMK for EHR PHI encryption"
  enable_key_rotation     = true
  deletion_window_in_days = 30
}

# --- Data services (declared in sibling files / TODO modules) ----------------
# eks.tf      -> EKS cluster + node groups + IRSA
# TODO(P1-7): msk.tf  -> Amazon MSK (Kafka), encryption in transit + at rest
# TODO(P1-7): rds.tf  -> Oracle (RDS) with TDE, multi-AZ, automated backups
# TODO(P1-7): s3.tf   -> buckets for FHIR $export + de-identified notes
