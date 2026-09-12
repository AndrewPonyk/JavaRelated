# frozen_string_literal: true

module Api
  module V1
    class AuthController < ApplicationController
      skip_before_action :authenticate_user!, only: %i[register login]

      def register
        user = User.create!(auth_params)
        render json: auth_response(user), status: :created
      end

      def login
        user = User.find_by(email: login_params.fetch(:email).to_s.strip.downcase)
        raise UnauthorizedError, 'Invalid email or password' unless user&.authenticate(login_params.fetch(:password))

        render json: auth_response(user), status: :ok
      end

      def logout
        AuthTokenService.revoke!(current_token_jti, current_token_expires_at)
        render json: { message: 'Logged out successfully' }, status: :ok
      end

      def me
        render json: { user: current_user }, status: :ok
      end

      private

      def auth_response(user)
        AuthTokenService.issue(user).merge(user:)
      end

      def auth_params
        params.require(:user).permit(:email, :password, :password_confirmation)
      end

      def login_params
        params.require(:user).permit(:email, :password).tap do |permitted|
          raise ActionController::ParameterMissing, :email if permitted[:email].blank?
          raise ActionController::ParameterMissing, :password if permitted[:password].blank?
        end
      end
    end
  end
end
