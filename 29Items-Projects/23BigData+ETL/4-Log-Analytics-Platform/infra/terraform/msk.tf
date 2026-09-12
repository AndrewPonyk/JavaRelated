# Amazon MSK — managed Kafka backbone.

resource "aws_security_group" "msk" {
  name_prefix = "${local.name_prefix}-msk-"
  vpc_id      = var.vpc_id

  # TODO: ingress 9094 (TLS) from ECS tasks SG + EMR Serverless SG only.
}

resource "aws_msk_cluster" "logs" {
  cluster_name           = local.name_prefix
  kafka_version          = "3.6.0"
  number_of_broker_nodes = 3 # one per AZ

  broker_node_group_info {
    instance_type   = var.kafka_broker_instance_type
    client_subnets  = var.private_subnet_ids
    security_groups = [aws_security_group.msk.id]

    storage_info {
      ebs_storage_info {
        volume_size = 500 # GB per broker; TODO: size from ingest-rate * retention math
      }
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }

  # TODO: client_authentication { sasl { iam = true } } + IAM-auth in clients
  #       (aws-msk-iam-sasl-signer for Python; Spark kafka options accordingly).
  # TODO: cloudwatch broker logs + open_monitoring (Prometheus JMX) for lag dashboards.
  # TODO: aws_msk_configuration with auto.create.topics.enable=false in prod.
}
