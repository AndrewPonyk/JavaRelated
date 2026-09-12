module Types
  class FollowType < Types::BaseObject
    field :id, ID, null: false
    field :follower, Types::UserType, null: false
    field :followee, Types::UserType, null: false
    field :notifications_enabled, Boolean, null: false
    field :created_at, GraphQL::Types::ISO8601DateTime, null: false
  end
end
