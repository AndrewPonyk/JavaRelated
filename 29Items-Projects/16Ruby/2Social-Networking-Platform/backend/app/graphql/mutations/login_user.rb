module Mutations
  class LoginUser < Mutations::BaseMutation
    argument :email, String, required: true
    argument :password, String, required: true

    field :auth_payload, Types::AuthPayloadType, null: true
    field :errors, [String], null: false

    def resolve(email:, password:)
      user = User.find_by(email: email.downcase)
      return { auth_payload: nil, errors: ["Invalid email or password"] } unless user&.authenticate(password)

      user.update!(last_seen_at: Time.current)

      {
        auth_payload: {
          user: user,
          access_token: Auth::TokenService.access_token_for(user),
          refresh_token: user.issue_refresh_token!
        },
        errors: []
      }
    end
  end
end
