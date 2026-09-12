module Api
  class FollowsController < ApplicationController
    before_action :authenticate_user!

    def index
      follows = visible_follows
                .includes(:follower, :followee)
                .order(created_at: :desc)
                .limit(bounded_limit(default: 50, maximum: 100))
      render json: follows.map { |follow| ResourceSerializer.follow(follow) }
    end

    def show
      follow = visible_follows.find(params[:id])
      render json: ResourceSerializer.follow(follow)
    end

    def create
      follow = current_user.following_relationships.create!(follow_params)
      render json: ResourceSerializer.follow(follow), status: :created
    end

    def update
      follow = current_user.following_relationships.find(params[:id])
      follow.update!(follow_params.slice(:notifications_enabled))
      render json: ResourceSerializer.follow(follow)
    end

    def destroy
      current_user.following_relationships.find(params[:id]).destroy!
      head :no_content
    end

    private

    def visible_follows
      Follow.where(follower_id: current_user.id).or(Follow.where(followee_id: current_user.id))
    end

    def follow_params
      params.require(:follow).permit(:followee_id, :notifications_enabled)
    end
  end
end
