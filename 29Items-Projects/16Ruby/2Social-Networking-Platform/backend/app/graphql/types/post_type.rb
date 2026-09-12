module Types
  class PostType < Types::BaseObject
    field :id, ID, null: false
    field :body, String, null: false
    field :visibility, String, null: false
    field :toxicity_score, Float, null: true
    field :user, Types::UserType, null: false
    field :reply_to_post_id, ID, null: true
    field :created_at, GraphQL::Types::ISO8601DateTime, null: false
  end
end
