output "vpc_id" {
  description = "ID of the platform VPC"
  value       = module.vpc.vpc_id
}

output "eks_cluster_name" {
  description = "EKS cluster name"
  value       = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  description = "EKS API server endpoint"
  value       = module.eks.cluster_endpoint
}

output "ecr_repository_urls" {
  description = "Map of service -> ECR repository URL"
  value       = { for name, repo in aws_ecr_repository.service : name => repo.repository_url }
}
