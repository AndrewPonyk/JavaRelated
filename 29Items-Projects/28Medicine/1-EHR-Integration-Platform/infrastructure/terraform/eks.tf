# EKS cluster + node groups. Skeleton (P1-7).

module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"

  cluster_name    = "ehr-${var.environment}"
  cluster_version = var.eks_cluster_version

  # Private API endpoint for HIPAA; nodes in private subnets only.
  cluster_endpoint_public_access = false
  vpc_id                         = module.vpc.vpc_id
  # subnet_ids                   = module.vpc.private_subnets  # (P1-7)

  # Encrypt secrets in etcd with the PHI CMK.
  cluster_encryption_config = {
    provider_key_arn = aws_kms_key.phi.arn
    resources        = ["secrets"]
  }

  eks_managed_node_groups = {
    general = {
      instance_types = ["m6i.large"]
      min_size       = 2
      max_size       = 6
      desired_size   = 3
    }
    # TODO(P1-7): memory-optimized group for Oracle clients; GPU group for ML inference.
  }

  # TODO(P1-4): enable IRSA so pods assume IAM roles (no static keys).
  enable_irsa = true
}

# TODO(P1-7): outputs (cluster endpoint, oidc provider arn) for downstream modules.
