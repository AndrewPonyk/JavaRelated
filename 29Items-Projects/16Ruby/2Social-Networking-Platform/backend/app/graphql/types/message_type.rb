module Types
  class MessageType < Types::BaseObject
    field :id, ID, null: false
    field :sender, Types::UserType, null: false
    field :recipient, Types::UserType, null: false
    field :body, String, null: false
    field :toxicity_score, Float, null: true
    field :read_at, GraphQL::Types::ISO8601DateTime, null: true
    field :created_at, GraphQL::Types::ISO8601DateTime, null: false
  end
end
