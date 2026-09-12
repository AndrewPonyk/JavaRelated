class CreateToxicityResults < ActiveRecord::Migration[7.1]
  def change
    create_table :toxicity_results do |t|
      t.references :post, foreign_key: true
      t.references :message, foreign_key: true
      t.decimal :score, precision: 5, scale: 4, null: false
      t.string :label, null: false
      t.string :model_version, null: false

      t.timestamps
    end

    add_index :toxicity_results, :score
    add_check_constraint :toxicity_results,
                         "(post_id IS NOT NULL) OR (message_id IS NOT NULL)",
                         name: "toxicity_results_target_present"
  end
end
