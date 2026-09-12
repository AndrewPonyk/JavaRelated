output "api_url" {
  value = google_cloud_run_v2_service.api.uri
}

output "elasticsearch_endpoint" {
  value     = ec_deployment.search.elasticsearch.https_endpoint
  sensitive = true
}

output "postgres_connection_name" {
  value = google_sql_database_instance.catalog.connection_name
}

output "redis_host" {
  value = google_redis_instance.suggest_cache.host
}
