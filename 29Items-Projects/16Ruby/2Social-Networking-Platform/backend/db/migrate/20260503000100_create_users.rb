class CreateUsers < ActiveRecord::Migration[7.1]
  def change
    create_table :users do |t|
      t.string :email, null: false
      t.string :username, null: false
      t.string :display_name
      t.string :password_digest
      t.string :refresh_token_digest
      t.datetime :refresh_token_expires_at
      t.datetime :last_seen_at

      t.timestamps
    end

    add_index :users, :email, unique: true
    add_index :users, :username, unique: true
    add_index :users, :refresh_token_digest
  end
end
