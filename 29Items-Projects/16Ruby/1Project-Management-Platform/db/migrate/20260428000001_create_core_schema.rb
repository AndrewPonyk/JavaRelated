class CreateCoreSchema < ActiveRecord::Migration[7.1]
  def change
    enable_extension "pgcrypto"
    enable_extension "pg_trgm"

    create_table :projects do |t|
      t.string :name, null: false
      t.string :key, null: false, limit: 10
      t.text :description
      t.timestamps
    end
    add_index :projects, :name, unique: true
    add_index :projects, :key, unique: true

    create_table :memberships do |t|
      t.references :user, null: false, foreign_key: true
      t.references :project, null: false, foreign_key: true
      t.string :role, null: false, default: "member"
      t.timestamps
    end
    add_index :memberships, %i[user_id project_id], unique: true

    create_table :sprints do |t|
      t.references :project, null: false, foreign_key: true
      t.string :name, null: false
      t.date :starts_on, null: false
      t.date :ends_on, null: false
      t.string :state, null: false, default: "planned"
      t.text :goal
      t.timestamps
    end
    add_index :sprints, %i[project_id state]

    create_table :issues do |t|
      t.references :project, null: false, foreign_key: true, index: true
      t.references :sprint, foreign_key: true, index: true
      t.references :reporter, null: false, foreign_key: { to_table: :users }
      t.references :assignee, foreign_key: { to_table: :users }
      t.string :title, null: false
      t.text :description
      t.string :status, null: false, default: "backlog"
      t.string :priority, null: false, default: "medium"
      t.decimal :estimate_hours, precision: 6, scale: 2
      t.timestamps
    end
    add_index :issues, %i[project_id status]
    add_index :issues, %i[sprint_id status]
    add_index :issues, :title, using: :gin, opclass: :gin_trgm_ops

    create_table :time_entries do |t|
      t.references :issue, null: false, foreign_key: true
      t.references :user, null: false, foreign_key: true
      t.decimal :hours, precision: 5, scale: 2, null: false
      t.date :worked_on, null: false
      t.text :note
      t.timestamps
    end
    add_index :time_entries, %i[user_id worked_on]

    create_table :workload_predictions do |t|
      t.references :sprint, null: false, foreign_key: true
      t.decimal :predicted_hours, precision: 8, scale: 2, null: false
      t.decimal :confidence, precision: 4, scale: 3, null: false
      t.string :model_version, null: false
      t.timestamps
    end
    add_index :workload_predictions, %i[sprint_id created_at]
  end
end
