module Api
  class UsersController < ApplicationController
    before_action :authenticate_user!, except: %i[index show create]

    def index
      users = User.order(:username).limit(bounded_limit(default: 50, maximum: 100))
      render json: users.map { |user| ResourceSerializer.user(user) }
    end

    def show
      render json: ResourceSerializer.user(User.find(params[:id]))
    end

    def create
      user = User.create!(user_params)
      render json: ResourceSerializer.user(user), status: :created
    end

    def update
      user = User.find(params[:id])
      authorize_self!(user)
      user.update!(user_params)
      render json: ResourceSerializer.user(user)
    end

    def destroy
      user = User.find(params[:id])
      authorize_self!(user)
      user.destroy!
      head :no_content
    end

    private

    def user_params
      params.require(:user).permit(:email, :username, :display_name, :password, :password_confirmation)
    end

    def authorize_self!(user)
      raise Auth::ForbiddenError, "You can only modify your own account" unless current_user.id == user.id
    end
  end
end
