module Types
  class AuthPayloadType < Types::BaseObject
    field :user, Types::UserType, null: false
    field :access_token, String, null: false
    field :refresh_token, String, null: false
  end
end
