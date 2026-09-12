module Types
  class UserType < Types::BaseObject
    field :id, ID, null: false
    field :email, String, null: false
    field :username, String, null: false
    field :display_name, String, null: true
    field :created_at, GraphQL::Types::ISO8601DateTime, null: false
  end
end
