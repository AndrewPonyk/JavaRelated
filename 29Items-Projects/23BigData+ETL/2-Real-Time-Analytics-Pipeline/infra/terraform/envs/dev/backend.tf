# Remote state — one S3 key per environment.
# TODO(bootstrap): create the state bucket + lock table once (console or a tiny
# bootstrap stack), then fill these in. Until then: terraform init -backend=false.
terraform {
  backend "s3" {
    bucket         = "CHANGE-ME-rtap-terraform-state"
    key            = "envs/dev/terraform.tfstate"
    region         = "eu-central-1"
    dynamodb_table = "CHANGE-ME-rtap-terraform-locks"
    encrypt        = true
  }
}
