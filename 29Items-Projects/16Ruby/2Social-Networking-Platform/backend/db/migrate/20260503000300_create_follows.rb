class CreateFollows < ActiveRecord::Migration[7.1]
  def change
    create_table :follows do |t|
      t.references :follower, null: false, foreign_key: { to_table: :users }
      t.references :followee, null: false, foreign_key: { to_table: :users }
      t.boolean :notifications_enabled, null: false, default: true

      t.timestamps
    end

    add_index :follows, %i[follower_id followee_id], unique: true
    add_index :follows, %i[followee_id created_at]
  end
end
