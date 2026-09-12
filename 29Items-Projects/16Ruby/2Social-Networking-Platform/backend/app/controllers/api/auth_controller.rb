module Api
  class AuthController < ApplicationController
    def register
      user = User.create!(register_params)
      render json: session_payload(user), status: :created
    end

    def login
      email = params.require(:email).to_s.downcase
      password = params.require(:password)
      user = User.find_by(email: email)
      raise Auth::UnauthorizedError, "Invalid email or password" unless user&.authenticate(password)

      user.update!(last_seen_at: Time.current)
      render json: session_payload(user)
    end

    def refresh
      user_id = params.require(:user_id)
      refresh_token = params.require(:refresh_token)
      user = User.find_by(id: user_id)
      raise Auth::UnauthorizedError, "Invalid refresh token" unless user&.authenticate_refresh_token(refresh_token)

      render json: session_payload(user)
    end

    def logout
      authenticate_user!
      current_user.revoke_refresh_token!
      head :no_content
    end

    def me
      authenticate_user!
      render json: { user: ResourceSerializer.user(current_user) }
    end

    private

    def register_params
      params.require(:user).permit(:email, :username, :display_name, :password, :password_confirmation)
    end

    def session_payload(user)
      {
        user: ResourceSerializer.user(user),
        access_token: Auth::TokenService.access_token_for(user),
        refresh_token: user.issue_refresh_token!
      }
    end
  end
end
