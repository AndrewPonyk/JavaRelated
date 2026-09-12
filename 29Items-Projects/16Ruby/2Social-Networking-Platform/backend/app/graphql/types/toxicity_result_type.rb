module Types
  class ToxicityResultType < Types::BaseObject
    field :id, ID, null: false
    field :post_id, ID, null: true
    field :message_id, ID, null: true
    field :score, Float, null: false
    field :label, String, null: false
    field :model_version, String, null: false
    field :created_at, GraphQL::Types::ISO8601DateTime, null: false
  end
end
