class CreateRevokedTokens < ActiveRecord::Migration[7.1]
  def change
    create_table :revoked_tokens, id: :uuid do |t|
      t.string :jti, null: false
      t.datetime :expires_at, null: false

      t.timestamps
    end

    add_index :revoked_tokens, :jti, unique: true
    add_index :revoked_tokens, :expires_at
  end
end
