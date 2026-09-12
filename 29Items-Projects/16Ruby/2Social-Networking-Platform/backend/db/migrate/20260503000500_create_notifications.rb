class CreateNotifications < ActiveRecord::Migration[7.1]
  def change
    create_table :notifications do |t|
      t.references :user, null: false, foreign_key: true
      t.string :kind, null: false
      t.jsonb :payload, null: false, default: {}
      t.datetime :read_at

      t.timestamps
    end

    add_index :notifications, %i[user_id read_at created_at]
  end
end
