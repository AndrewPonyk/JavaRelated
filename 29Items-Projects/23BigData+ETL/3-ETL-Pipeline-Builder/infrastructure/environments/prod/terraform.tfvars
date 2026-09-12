# prod — full sizing; changes land only through infra.yml with approval.
env               = "prod"
msk_broker_count  = 3
msk_instance_type = "kafka.m7g.large"
msk_ebs_gb        = 1000
