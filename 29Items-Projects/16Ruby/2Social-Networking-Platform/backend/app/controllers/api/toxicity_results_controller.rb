module Api
  class ToxicityResultsController < ApplicationController
    before_action :authenticate_user!

    def index
      results = ToxicityResult
                .includes(:post, :message)
                .order(created_at: :desc)
                .limit(bounded_limit(default: 50, maximum: 100))
                .select { |result| visible_result?(result) }
      render json: results.map { |result| ResourceSerializer.toxicity_result(result) }
    end

    def show
      result = ToxicityResult.find(params[:id])
      raise Auth::ForbiddenError, "You are not allowed to access this result" unless visible_result?(result)

      render json: ResourceSerializer.toxicity_result(result)
    end

    def create
      attributes = toxicity_result_params
      raise Auth::ForbiddenError, "You are not allowed to create this result" unless visible_target?(attributes)

      result = ToxicityResult.create!(attributes)
      render json: ResourceSerializer.toxicity_result(result), status: :created
    end

    def update
      result = ToxicityResult.find(params[:id])
      raise Auth::ForbiddenError, "You are not allowed to update this result" unless visible_result?(result)

      result.update!(toxicity_result_params)
      render json: ResourceSerializer.toxicity_result(result)
    end

    def destroy
      result = ToxicityResult.find(params[:id])
      raise Auth::ForbiddenError, "You are not allowed to delete this result" unless visible_result?(result)

      result.destroy!
      head :no_content
    end

    private

    def toxicity_result_params
      params.require(:toxicity_result).permit(:post_id, :message_id, :score, :label, :model_version)
    end

    def visible_result?(result)
      return result.post.user_id == current_user.id if result.post
      return [result.message.sender_id, result.message.recipient_id].include?(current_user.id) if result.message

      false
    end

    def visible_target?(attributes)
      if attributes[:post_id].present?
        Post.find(attributes[:post_id]).user_id == current_user.id
      elsif attributes[:message_id].present?
        message = Message.find(attributes[:message_id])
        [message.sender_id, message.recipient_id].include?(current_user.id)
      else
        false
      end
    end
  end
end
