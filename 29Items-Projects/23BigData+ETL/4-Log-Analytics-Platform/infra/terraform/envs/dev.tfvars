environment = "dev"
region      = "eu-central-1"

# TODO: real network ids
vpc_id             = "vpc-CHANGEME"
private_subnet_ids = ["subnet-CHANGEME-a", "subnet-CHANGEME-b", "subnet-CHANGEME-c"]

# Dev runs small.
kafka_broker_instance_type = "kafka.t3.small"
opensearch_instance_type   = "t3.small.search"
opensearch_instance_count  = 1
opensearch_volume_size_gb  = 50
emr_max_cpu                = "16 vCPU"
emr_max_memory             = "64 GB"
