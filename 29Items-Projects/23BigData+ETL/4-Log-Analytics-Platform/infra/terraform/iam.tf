# IAM: GitHub Actions OIDC federation (no long-lived AWS keys) + task/job roles.

data "aws_caller_identity" "current" {}

resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"] # GitHub's root CA thumbprint
}

locals {
  github_sub_main = "repo:${var.github_repository}:ref:refs/heads/main"
  github_sub_env  = "repo:${var.github_repository}:environment:${var.environment}"
}

# CI role: push images to ECR (main branch only).
resource "aws_iam_role" "github_ci" {
  name = "${local.name_prefix}-github-ci"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = { "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com" }
        StringLike   = { "token.actions.githubusercontent.com:sub" = local.github_sub_main }
      }
    }]
  })
  # TODO: attach least-privilege ECR push policy (GetAuthorizationToken, BatchCheckLayerAvailability,
  #       PutImage, InitiateLayerUpload… scoped to the two repositories).
}

# Deploy role: assumed via GitHub *environment* (approval-gated for staging/prod).
resource "aws_iam_role" "github_deploy" {
  name = "${local.name_prefix}-github-deploy"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = { "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com" }
        StringLike   = { "token.actions.githubusercontent.com:sub" = local.github_sub_env }
      }
    }]
  })
  # TODO: least-privilege policy — ecs:UpdateService/DescribeServices, s3:PutObject (artifacts),
  #       emr-serverless:StartJobRun/CancelJobRun, es:ESHttp* on the domain (migrations).
}

# TODO: aws_iam_role.task_execution (pull image, write logs, read secrets)
# TODO: aws_iam_role.api_task / gateway_task / alerting_task (es:ESHttp*, MSK connect, secrets)
# TODO: aws_iam_role.emr_job (S3 artifacts+checkpoints+models, MSK, es:ESHttp*)
