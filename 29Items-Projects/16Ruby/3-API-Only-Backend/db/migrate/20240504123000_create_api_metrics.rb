class CreateApiMetrics < ActiveRecord::Migration[7.1]
  def change
    create_table :api_metrics, id: :uuid do |t|
      t.references :user, foreign_key: { on_delete: :nullify }, type: :uuid
      t.string :method, null: false
      t.string :path, null: false
      t.integer :status, null: false
      t.float :latency_ms, null: false
      t.datetime :occurred_at, null: false
      t.jsonb :metadata, null: false, default: {}

      t.timestamps
    end

    add_index :api_metrics, :occurred_at
    add_index :api_metrics, :status
    add_index :api_metrics, %i[method path]
  end
end
