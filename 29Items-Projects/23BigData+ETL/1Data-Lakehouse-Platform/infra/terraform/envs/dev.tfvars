environment = "dev"
aws_region  = "eu-central-1"

# Empty networking defers MSK creation (see modules/msk) so the storage and
# processing stacks apply first; populate from the network stack outputs.
vpc_id             = ""
private_subnet_ids = []
