module Mutations
  class RegisterUser < Mutations::BaseMutation
    argument :email, String, required: true
    argument :username, String, required: true
    argument :display_name, String, required: false
    argument :password, String, required: true

    field :auth_payload, Types::AuthPayloadType, null: true
    field :errors, [String], null: false

    def resolve(email:, username:, password:, display_name: nil)
      user = User.create!(
        email: email,
        username: username,
        display_name: display_name,
        password: password,
        password_confirmation: password
      )

      {
        auth_payload: {
          user: user,
          access_token: Auth::TokenService.access_token_for(user),
          refresh_token: user.issue_refresh_token!
        },
        errors: []
      }
    rescue ActiveRecord::RecordInvalid => e
      { auth_payload: nil, errors: e.record.errors.full_messages }
    end
  end
end
