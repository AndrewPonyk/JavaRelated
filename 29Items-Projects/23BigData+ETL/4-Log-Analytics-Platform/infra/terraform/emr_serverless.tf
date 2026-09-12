# EMR Serverless — Spark runtime for the three streaming jobs + scheduled training.

resource "aws_emrserverless_application" "spark" {
  name          = "${local.name_prefix}-spark"
  release_label = "emr-7.2.0" # Spark 3.5.x — matches requirements-spark.txt
  type          = "spark"

  maximum_capacity {
    cpu    = var.emr_max_cpu
    memory = var.emr_max_memory
  }

  # Streaming jobs are long-running: keep warm capacity for the drivers.
  initial_capacity {
    initial_capacity_type = "Driver"
    initial_capacity_config {
      worker_count = 3 # one driver per streaming job
      worker_configuration {
        cpu    = "2 vCPU"
        memory = "4 GB"
      }
    }
  }

  auto_stop_configuration {
    enabled              = false # never auto-stop an app hosting streaming jobs
    idle_timeout_minutes = 15
  }

  network_configuration {
    subnet_ids         = var.private_subnet_ids
    security_group_ids = [aws_security_group.emr.id]
  }
}

resource "aws_security_group" "emr" {
  name_prefix = "${local.name_prefix}-emr-"
  vpc_id      = var.vpc_id
  # TODO: egress to MSK:9094, OpenSearch:443, S3 gateway endpoint.
}

resource "aws_s3_bucket" "artifacts" {
  bucket = "${local.name_prefix}-artifacts" # spark code zips, checkpoints, model registry

  # TODO: versioning, SSE-KMS, lifecycle (expire old code artifacts after 90d,
  #       never expire checkpoints/ and models/ prefixes).
}

# TODO: aws_scheduler_schedule for the weekly ml/train.py batch job run.
