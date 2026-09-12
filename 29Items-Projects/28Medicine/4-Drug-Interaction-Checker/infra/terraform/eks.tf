# EKS cluster skeleton using the community terraform-aws-modules.
# Uncomment and pin versions once the VPC is defined.

# module "vpc" {
#   source  = "terraform-aws-modules/vpc/aws"
#   version = "~> 5.0"
#   name    = "${var.cluster_name}-vpc"
#   cidr    = "10.0.0.0/16"
#   azs             = ["${var.aws_region}a", "${var.aws_region}b", "${var.aws_region}c"]
#   private_subnets = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
#   public_subnets  = ["10.0.101.0/24", "10.0.102.0/24", "10.0.103.0/24"]
#   enable_nat_gateway = true
#   single_nat_gateway = true
# }

# module "eks" {
#   source  = "terraform-aws-modules/eks/aws"
#   version = "~> 20.0"
#
#   cluster_name    = var.cluster_name
#   cluster_version = var.kubernetes_version
#   vpc_id          = module.vpc.vpc_id
#   subnet_ids      = module.vpc.private_subnets
#
#   cluster_endpoint_public_access = true
#   enable_irsa                    = true
#
#   eks_managed_node_groups = {
#     default = {
#       instance_types = var.node_instance_types
#       min_size       = 2
#       max_size       = 6
#       desired_size   = 3
#     }
#   }
# }

# TODO: aws-load-balancer-controller (Helm), external-secrets (Helm),
# IRSA role for the ml-severity service account (S3 read on the models bucket).
