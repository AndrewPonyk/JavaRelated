# Bedrock access for the application's task role.
#
# NOTE: Model *access* must also be granted in the Bedrock console / via
# `aws bedrock put-model-invocation-logging-configuration` + model-access requests for
# each model in each region — inference returns 403 until access is approved. This file
# codifies the IAM permission to invoke once access is granted.

data "aws_caller_identity" "current" {}

# Least-privilege policy: only InvokeModel* on the allowlisted model ARNs.
data "aws_iam_policy_document" "bedrock_invoke" {
  statement {
    sid    = "InvokeAllowedModels"
    effect = "Allow"
    actions = [
      "bedrock:InvokeModel",
      "bedrock:InvokeModelWithResponseStream",
    ]
    resources = [
      for model_id in var.bedrock_model_ids :
      "arn:aws:bedrock:${var.aws_region}::foundation-model/${model_id}"
    ]
  }
}

resource "aws_iam_policy" "bedrock_invoke" {
  name   = "docintel-${var.environment}-bedrock-invoke"
  policy = data.aws_iam_policy_document.bedrock_invoke.json
}

# Trust policy: assumed by the ECS task role (the API + worker).
data "aws_iam_policy_document" "ecs_task_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "app_task" {
  name               = "docintel-${var.environment}-app-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume.json
}

resource "aws_iam_role_policy_attachment" "app_bedrock" {
  role       = aws_iam_role.app_task.name
  policy_arn = aws_iam_policy.bedrock_invoke.arn
}

# TODO: attach S3 (documents bucket) + SQS (ingestion queue) + Secrets Manager read
#       policies to aws_iam_role.app_task with the same least-privilege approach.
