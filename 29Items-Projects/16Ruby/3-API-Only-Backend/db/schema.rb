# This file is auto-generated from the current state of the database. Instead
# of editing this file, please use the migrations feature of Active Record to
# incrementally modify your database, and then regenerate this schema definition.
#
# This file is the source Rails uses to define your schema when running `bin/rails
# db:schema:load`. When creating a new database, `bin/rails db:schema:load` tends to
# be faster and is potentially less error prone than running all of your
# migrations from scratch. Old migrations may fail to apply correctly if those
# migrations use external dependencies or application code.
#
# It's strongly recommended that you check this file into your version control system.

ActiveRecord::Schema[7.1].define(version: 2024_05_04_124000) do
  # These are extensions that must be enabled in order to support this database
  enable_extension "pgcrypto"
  enable_extension "plpgsql"

  create_table "anomalies", id: :uuid, default: -> { "gen_random_uuid()" }, force: :cascade do |t|
    t.string "metric_name", null: false
    t.string "severity", null: false
    t.text "description", null: false
    t.float "baseline_value", default: 0.0, null: false
    t.float "observed_value", null: false
    t.datetime "detected_at", null: false
    t.datetime "resolved_at"
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["detected_at"], name: "index_anomalies_on_detected_at"
    t.index ["metric_name"], name: "index_anomalies_on_metric_name"
    t.index ["resolved_at"], name: "index_anomalies_on_resolved_at"
    t.index ["severity"], name: "index_anomalies_on_severity"
  end

  create_table "api_metrics", id: :uuid, default: -> { "gen_random_uuid()" }, force: :cascade do |t|
    t.uuid "user_id"
    t.string "method", null: false
    t.string "path", null: false
    t.integer "status", null: false
    t.float "latency_ms", null: false
    t.datetime "occurred_at", null: false
    t.jsonb "metadata", default: {}, null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["method", "path"], name: "index_api_metrics_on_method_and_path"
    t.index ["occurred_at"], name: "index_api_metrics_on_occurred_at"
    t.index ["status"], name: "index_api_metrics_on_status"
    t.index ["user_id"], name: "index_api_metrics_on_user_id"
  end

  create_table "revoked_tokens", id: :uuid, default: -> { "gen_random_uuid()" }, force: :cascade do |t|
    t.string "jti", null: false
    t.datetime "expires_at", null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["expires_at"], name: "index_revoked_tokens_on_expires_at"
    t.index ["jti"], name: "index_revoked_tokens_on_jti", unique: true
  end

  create_table "sync_items", id: :uuid, default: -> { "gen_random_uuid()" }, force: :cascade do |t|
    t.uuid "user_id", null: false
    t.string "collection_name", null: false
    t.string "record_id", null: false
    t.jsonb "payload", default: {}, null: false
    t.datetime "client_updated_at", null: false
    t.datetime "last_synced_at", null: false
    t.datetime "deleted_at"
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["deleted_at"], name: "index_sync_items_on_deleted_at"
    t.index ["user_id", "collection_name", "record_id"], name: "index_sync_items_on_user_id_and_collection_name_and_record_id", unique: true
    t.index ["user_id", "last_synced_at"], name: "index_sync_items_on_user_id_and_last_synced_at"
    t.index ["user_id"], name: "index_sync_items_on_user_id"
  end

  create_table "users", id: :uuid, default: -> { "gen_random_uuid()" }, force: :cascade do |t|
    t.string "email", null: false
    t.string "password_digest", null: false
    t.string "jti", null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["email"], name: "index_users_on_email", unique: true
    t.index ["jti"], name: "index_users_on_jti", unique: true
  end

  add_foreign_key "api_metrics", "users", on_delete: :nullify
  add_foreign_key "sync_items", "users"
end
