# frozen_string_literal: true

module Api
  module V1
    class UsersController < ApplicationController
      skip_before_action :authenticate_user!, only: :create
      before_action :set_user, only: %i[show update destroy]
      before_action -> { authorize_user!(@user) }, only: %i[show update destroy]

      def index
        users, meta = paginated(User.where(id: current_user.id).order(created_at: :desc))
        render json: { users:, meta: }, status: :ok
      end

      def show
        render json: { user: @user }, status: :ok
      end

      def create
        user = User.create!(user_params)
        render json: { user: }, status: :created
      end

      def update
        @user.update!(user_params)
        render json: { user: @user }, status: :ok
      end

      def destroy
        @user.destroy!
        render json: { message: 'User deleted successfully' }, status: :ok
      end

      private

      def set_user
        @user = User.find(params[:id])
      end

      def user_params
        params.require(:user).permit(:email, :password, :password_confirmation)
      end
    end
  end
end
