# Amazon Managed Grafana — dashboards over OpenSearch + PostgreSQL,
# SSO via IAM Identity Center. Local equivalent: grafana/ provisioning in compose.

variable "name" { type = string }
variable "tags" {
  type    = map(string)
  default = {}
}

resource "aws_iam_role" "grafana" {
  name = "${var.name}-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "grafana.amazonaws.com" }
    }]
  })
  tags = var.tags
}

resource "aws_grafana_workspace" "this" {
  name                     = var.name
  account_access_type      = "CURRENT_ACCOUNT"
  authentication_providers = ["AWS_SSO"]
  permission_type          = "SERVICE_MANAGED"
  role_arn                 = aws_iam_role.grafana.arn
  data_sources             = ["AMAZON_OPENSEARCH_SERVICE"]

  # TODO: vpc_configuration to reach the private PostgreSQL; PG datasource with the
  #       grafana_ro role (password from Secrets Manager).
  # TODO: provision dashboards from grafana/dashboards/*.json via the Grafana API
  #       (terraform grafana provider) so local and AWS stay in sync.

  tags = var.tags
}

output "workspace_endpoint" { value = aws_grafana_workspace.this.endpoint }
