project_id        = "my-search-project-prod" # replace with your GCP project id
environment       = "prod"
es_node_size      = "4g"
es_zone_count     = 2 # HA across zones
db_tier           = "db-custom-2-7680"
redis_memory_gb   = 2
api_min_instances = 1 # avoid cold starts on the search path
api_max_instances = 20
