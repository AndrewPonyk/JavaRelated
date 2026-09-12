class CreatePosts < ActiveRecord::Migration[7.1]
  def change
    create_table :posts do |t|
      t.references :user, null: false, foreign_key: true
      t.text :body, null: false
      t.string :visibility, null: false, default: "public"
      t.integer :reply_to_post_id

      t.timestamps
    end

    add_index :posts, %i[user_id created_at]
    add_index :posts, :created_at
    add_foreign_key :posts, :posts, column: :reply_to_post_id
  end
end
