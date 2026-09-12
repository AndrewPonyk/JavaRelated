module Api
  class PostsController < ApplicationController
    before_action :authenticate_user!, except: %i[index show]

    def index
      posts = Post.visible_to(current_user)
                  .includes(:toxicity_result)
                  .order(created_at: :desc)
                  .limit(bounded_limit(default: 50, maximum: 100))
      render json: posts.map { |post| ResourceSerializer.post(post) }
    end

    def show
      post = Post.visible_to(current_user).find(params[:id])
      render json: ResourceSerializer.post(post)
    end

    def create
      result = Posts::CreatePost.call(user: current_user, attributes: post_params.to_h)

      if result.success?
        render json: ResourceSerializer.post(result.post), status: :created
      else
        render json: { errors: result.errors }, status: :unprocessable_entity
      end
    end

    def update
      post = current_user.posts.find(params[:id])
      post.update!(post_params)
      IndexPostJob.perform_later(post.id)
      render json: ResourceSerializer.post(post)
    end

    def destroy
      current_user.posts.find(params[:id]).destroy!
      head :no_content
    end

    private

    def post_params
      params.require(:post).permit(:body, :visibility, :reply_to_post_id)
    end
  end
end
