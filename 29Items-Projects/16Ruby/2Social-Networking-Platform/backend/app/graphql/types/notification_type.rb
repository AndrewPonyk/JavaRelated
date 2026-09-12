module Types
  class NotificationType < Types::BaseObject
    field :id, ID, null: false
    field :kind, String, null: false
    field :payload, GraphQL::Types::JSON, null: false
    field :read_at, GraphQL::Types::ISO8601DateTime, null: true
    field :created_at, GraphQL::Types::ISO8601DateTime, null: false
  end
end
