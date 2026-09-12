class CreateUsers < ActiveRecord::Migration[7.1]
  def change
    create_table :users, id: :uuid do |t|
      t.string :email, null: false
      t.string :password_digest, null: false
      
      # For JWT revocation strategy (optional but recommended)
      t.string :jti, null: false
      
      t.timestamps
    end

    add_index :users, :email, unique: true
    add_index :users, :jti, unique: true
  end
end
