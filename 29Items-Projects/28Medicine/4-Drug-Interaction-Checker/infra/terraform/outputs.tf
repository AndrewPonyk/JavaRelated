output "backend_ecr_url" {
  description = "ECR repository URL for the backend image"
  value       = aws_ecr_repository.backend.repository_url
}

output "frontend_ecr_url" {
  description = "ECR repository URL for the frontend image"
  value       = aws_ecr_repository.frontend.repository_url
}

output "model_bucket" {
  description = "S3 bucket holding ML model artifacts"
  value       = aws_s3_bucket.models.bucket
}

# output "cluster_name" {
#   value = module.eks.cluster_name
# }
# output "cluster_endpoint" {
#   value = module.eks.cluster_endpoint
# }
