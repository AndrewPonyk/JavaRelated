ActiveRecord::Schema[7.1].define(version: 2026_04_28_000002) do
  enable_extension "pgcrypto"
  enable_extension "pg_trgm"
  enable_extension "plpgsql"

  create_table "comments", force: :cascade do |t|
    t.bigint "issue_id", null: false
    t.bigint "user_id", null: false
    t.text "body", null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index %w[issue_id created_at], name: "index_comments_on_issue_id_and_created_at"
    t.index ["issue_id"], name: "index_comments_on_issue_id"
    t.index ["user_id"], name: "index_comments_on_user_id"
  end

  create_table "issues", force: :cascade do |t|
    t.bigint "project_id", null: false
    t.bigint "sprint_id"
    t.bigint "reporter_id", null: false
    t.bigint "assignee_id"
    t.string "title", null: false
    t.text "description"
    t.string "status", default: "backlog", null: false
    t.string "priority", default: "medium", null: false
    t.decimal "estimate_hours", precision: 6, scale: 2
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["assignee_id"], name: "index_issues_on_assignee_id"
    t.index ["project_id", "status"], name: "index_issues_on_project_id_and_status"
    t.index ["project_id"], name: "index_issues_on_project_id"
    t.index ["reporter_id"], name: "index_issues_on_reporter_id"
    t.index ["sprint_id", "status"], name: "index_issues_on_sprint_id_and_status"
    t.index ["sprint_id"], name: "index_issues_on_sprint_id"
    t.index ["title"], name: "index_issues_on_title", opclass: :gin_trgm_ops, using: :gin
  end

  create_table "memberships", force: :cascade do |t|
    t.bigint "user_id", null: false
    t.bigint "project_id", null: false
    t.string "role", default: "member", null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["project_id"], name: "index_memberships_on_project_id"
    t.index %w[user_id project_id], name: "index_memberships_on_user_id_and_project_id", unique: true
    t.index ["user_id"], name: "index_memberships_on_user_id"
  end

  create_table "projects", force: :cascade do |t|
    t.string "name", null: false
    t.string "key", limit: 10, null: false
    t.text "description"
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["key"], name: "index_projects_on_key", unique: true
    t.index ["name"], name: "index_projects_on_name", unique: true
  end

  create_table "sprints", force: :cascade do |t|
    t.bigint "project_id", null: false
    t.string "name", null: false
    t.date "starts_on", null: false
    t.date "ends_on", null: false
    t.string "state", default: "planned", null: false
    t.text "goal"
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["project_id", "state"], name: "index_sprints_on_project_id_and_state"
    t.index ["project_id"], name: "index_sprints_on_project_id"
  end

  create_table "time_entries", force: :cascade do |t|
    t.bigint "issue_id", null: false
    t.bigint "user_id", null: false
    t.decimal "hours", precision: 5, scale: 2, null: false
    t.date "worked_on", null: false
    t.text "note"
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["issue_id"], name: "index_time_entries_on_issue_id"
    t.index %w[user_id worked_on], name: "index_time_entries_on_user_id_and_worked_on"
    t.index ["user_id"], name: "index_time_entries_on_user_id"
  end

  create_table "users", force: :cascade do |t|
    t.string "email", default: "", null: false
    t.string "encrypted_password", default: "", null: false
    t.string "name", default: "", null: false
    t.string "api_token"
    t.boolean "admin_flag", default: false, null: false
    t.string "reset_password_token"
    t.datetime "reset_password_sent_at"
    t.datetime "remember_created_at"
    t.integer "sign_in_count", default: 0, null: false
    t.datetime "current_sign_in_at"
    t.datetime "last_sign_in_at"
    t.string "current_sign_in_ip"
    t.string "last_sign_in_ip"
    t.string "provider"
    t.string "uid"
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index ["api_token"], name: "index_users_on_api_token", unique: true
    t.index ["email"], name: "index_users_on_email", unique: true
    t.index %w[provider uid], name: "index_users_on_provider_and_uid"
    t.index ["reset_password_token"], name: "index_users_on_reset_password_token", unique: true
  end

  create_table "workload_predictions", force: :cascade do |t|
    t.bigint "sprint_id", null: false
    t.decimal "predicted_hours", precision: 8, scale: 2, null: false
    t.decimal "confidence", precision: 4, scale: 3, null: false
    t.string "model_version", null: false
    t.datetime "created_at", null: false
    t.datetime "updated_at", null: false
    t.index %w[sprint_id created_at], name: "index_workload_predictions_on_sprint_id_and_created_at"
    t.index ["sprint_id"], name: "index_workload_predictions_on_sprint_id"
  end

  add_foreign_key "comments", "issues"
  add_foreign_key "comments", "users"
  add_foreign_key "issues", "projects"
  add_foreign_key "issues", "sprints"
  add_foreign_key "issues", "users", column: "assignee_id"
  add_foreign_key "issues", "users", column: "reporter_id"
  add_foreign_key "memberships", "projects"
  add_foreign_key "memberships", "users"
  add_foreign_key "sprints", "projects"
  add_foreign_key "time_entries", "issues"
  add_foreign_key "time_entries", "users"
  add_foreign_key "workload_predictions", "sprints"
end
