# DEV sizing & artifact pins. Artifact keys/images are normally overridden by CD
# (-var) with the current git SHA; these values are the bootstrap defaults.
environment         = "dev"
region              = "eu-central-1"
msk_instance_type   = "kafka.t3.small"
aggregation_jar_key = "flink/bootstrap/flink-aggregation-job-0.1.0-SNAPSHOT.jar"
anomaly_jar_key     = "flink/bootstrap/flink-anomaly-job-0.1.0-SNAPSHOT.jar"
api_image           = "CHANGE-ME.dkr.ecr.eu-central-1.amazonaws.com/rtap-analytics-api:bootstrap"
