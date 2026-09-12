environment = "staging"
region      = "eu-central-1"

# TODO: real network ids
vpc_id             = "vpc-CHANGEME"
private_subnet_ids = ["subnet-CHANGEME-a", "subnet-CHANGEME-b", "subnet-CHANGEME-c"]

kafka_broker_instance_type = "kafka.m7g.large"
opensearch_instance_type   = "r7g.large.search"
opensearch_instance_count  = 2
opensearch_volume_size_gb  = 100
