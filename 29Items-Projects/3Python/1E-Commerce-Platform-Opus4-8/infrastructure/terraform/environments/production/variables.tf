variable "aws_region" {
  type    = string
  default = "eu-central-1"
}

variable "web_image" {
  type        = string
  description = "Fully-qualified ECR image URI (tagged by git SHA/tag)."
}
