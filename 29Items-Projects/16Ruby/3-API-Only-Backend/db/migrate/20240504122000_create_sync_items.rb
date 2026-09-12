class CreateSyncItems < ActiveRecord::Migration[7.1]
  def change
    create_table :sync_items, id: :uuid do |t|
      t.references :user, null: false, foreign_key: true, type: :uuid
      t.string :collection_name, null: false
      t.string :record_id, null: false
      t.jsonb :payload, null: false, default: {}
      t.datetime :client_updated_at, null: false
      t.datetime :last_synced_at, null: false
      t.datetime :deleted_at

      t.timestamps
    end

    add_index :sync_items, %i[user_id collection_name record_id], unique: true
    add_index :sync_items, %i[user_id last_synced_at]
    add_index :sync_items, :deleted_at
  end
end
