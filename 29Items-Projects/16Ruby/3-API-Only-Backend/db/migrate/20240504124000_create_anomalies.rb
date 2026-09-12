class CreateAnomalies < ActiveRecord::Migration[7.1]
  def change
    create_table :anomalies, id: :uuid do |t|
      t.string :metric_name, null: false
      t.string :severity, null: false
      t.text :description, null: false
      t.float :baseline_value, null: false, default: 0
      t.float :observed_value, null: false
      t.datetime :detected_at, null: false
      t.datetime :resolved_at

      t.timestamps
    end

    add_index :anomalies, :metric_name
    add_index :anomalies, :severity
    add_index :anomalies, :detected_at
    add_index :anomalies, :resolved_at
  end
end
