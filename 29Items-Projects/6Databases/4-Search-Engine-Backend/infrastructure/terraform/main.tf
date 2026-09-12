# GCP infrastructure for the Search Engine Backend.
# One workspace/state per environment; sizing comes from environments/<env>.tfvars.
# State backend and network wiring are environment-specific choices — set the
# commented blocks to your organization's values before the first apply.

terraform {
  required_version = ">= 1.7"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.30"
    }
    ec = {
      source  = "elastic/ec" # Elastic Cloud (Elasticsearch on GCP)
      version = "~> 0.9"
    }
  }
  # backend "gcs" {
  #   bucket = "<your-tf-state-bucket>"
  #   prefix = "search-backend"
  # }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

provider "ec" {} # reads EC_API_KEY from the environment (CI secret)

locals {
  image = "${var.region}-docker.pkg.dev/${var.project_id}/search/search-api:latest"
}

# --- Container registry ---------------------------------------------------
resource "google_artifact_registry_repository" "search" {
  repository_id = "search"
  location      = var.region
  format        = "DOCKER"
}

# --- Secrets (values are set out-of-band, never in Terraform state) --------
resource "google_secret_manager_secret" "app" {
  for_each  = toset(["database-url", "es-api-key", "admin-api-key"])
  secret_id = "${var.environment}-${each.key}"
  replication {
    auto {}
  }
}

# --- Elasticsearch (Elastic Cloud on GCP) ---------------------------------
# NOTE: managed Elastic Cloud cannot install the LTR plugin — use LTR_MODE=native,
# or replace this with a self-managed ES on GKE module if the plugin is required.
resource "ec_deployment" "search" {
  name                   = "search-${var.environment}"
  region                 = "gcp-${var.region}"
  version                = var.elasticsearch_version
  deployment_template_id = "gcp-storage-optimized"

  elasticsearch = {
    hot = {
      size        = var.es_node_size
      zone_count  = var.es_zone_count
      autoscaling = {}
    }
  }

  kibana = {}
}

# --- PostgreSQL (Cloud SQL) ------------------------------------------------
resource "google_sql_database_instance" "catalog" {
  name             = "search-catalog-${var.environment}"
  database_version = "POSTGRES_16"
  region           = var.region

  settings {
    tier = var.db_tier
    ip_configuration {
      ipv4_enabled = false # private IP only
      # private_network = "projects/<project>/global/networks/<vpc>"  # your VPC
    }
    backup_configuration {
      enabled                        = true
      point_in_time_recovery_enabled = true
    }
  }
}

resource "google_sql_database" "search" {
  name     = "search"
  instance = google_sql_database_instance.catalog.name
}

# --- Redis (Memorystore) ----------------------------------------------------
resource "google_redis_instance" "suggest_cache" {
  name           = "search-suggest-${var.environment}"
  tier           = var.environment == "prod" ? "STANDARD_HA" : "BASIC"
  memory_size_gb = var.redis_memory_gb
  region         = var.region
}

# --- API (Cloud Run service) -------------------------------------------------
resource "google_cloud_run_v2_service" "api" {
  name     = "search-api-${var.environment}"
  location = var.region

  template {
    scaling {
      min_instance_count = var.api_min_instances
      max_instance_count = var.api_max_instances
    }
    containers {
      # Image tags are moved by CI (deploy-*.yml); Terraform owns the service shell.
      image = local.image
      env {
        name  = "ENVIRONMENT"
        value = var.environment
      }
      env {
        name  = "REDIS_URL"
        value = "redis://${google_redis_instance.suggest_cache.host}:6379/0"
      }
      env {
        name  = "ES_URL"
        value = ec_deployment.search.elasticsearch.https_endpoint
      }
      dynamic "env" {
        for_each = { DATABASE_URL = "database-url", ES_API_KEY = "es-api-key", ADMIN_API_KEY = "admin-api-key" }
        content {
          name = env.key
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.app[env.value].secret_id
              version = "latest"
            }
          }
        }
      }
    }
    # vpc_access { connector = "<your-serverless-vpc-connector>" }  # for private SQL/Redis
  }

  lifecycle {
    ignore_changes = [template[0].containers[0].image] # CI moves the image tag
  }
}

# --- Migrations (Cloud Run Job, executed by the deploy workflows) -----------
resource "google_cloud_run_v2_job" "migrate" {
  name     = "search-migrate-${var.environment}"
  location = var.region

  template {
    template {
      containers {
        image   = local.image
        command = ["python"]
        args    = ["-m", "alembic", "upgrade", "head"]
        dynamic "env" {
          for_each = { DATABASE_URL = "database-url" }
          content {
            name = env.key
            value_source {
              secret_key_ref {
                secret  = google_secret_manager_secret.app[env.value].secret_id
                version = "latest"
              }
            }
          }
        }
      }
    }
  }

  lifecycle {
    ignore_changes = [template[0].template[0].containers[0].image]
  }
}

# --- Frontend hosting (static bucket; front with Cloud CDN/LB) ---------------
resource "google_storage_bucket" "web" {
  name                        = "search-web-${var.environment}-${var.project_id}"
  location                    = var.region
  uniform_bucket_level_access = true
  website {
    main_page_suffix = "index.html"
    not_found_page   = "index.html" # SPA fallback
  }
}

resource "google_storage_bucket_iam_member" "web_public" {
  bucket = google_storage_bucket.web.name
  role   = "roles/storage.objectViewer"
  member = "allUsers"
}
