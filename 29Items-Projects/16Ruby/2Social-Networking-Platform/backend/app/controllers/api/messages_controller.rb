module Api
  class MessagesController < ApplicationController
    before_action :authenticate_user!

    def index
      messages = Message.visible_to(current_user)
                        .includes(:sender, :recipient, :toxicity_result)
                        .order(created_at: :desc)
                        .limit(bounded_limit(default: 50, maximum: 100))
      render json: messages.map { |message| ResourceSerializer.message(message) }
    end

    def show
      message = Message.visible_to(current_user).find(params[:id])
      render json: ResourceSerializer.message(message)
    end

    def create
      result = Messages::CreateMessage.call(sender: current_user, attributes: message_params.to_h)

      if result.success?
        render json: ResourceSerializer.message(result.message), status: :created
      else
        render json: { errors: result.errors }, status: :unprocessable_entity
      end
    end

    def update
      message = Message.visible_to(current_user).find(params[:id])
      attrs = message_update_params.to_h
      raise Auth::ForbiddenError, "Only the sender can edit message body" if attrs.key?("body") && message.sender_id != current_user.id

      message.update!(attrs)
      render json: ResourceSerializer.message(message)
    end

    def destroy
      Message.visible_to(current_user).find(params[:id]).destroy!
      head :no_content
    end

    private

    def message_params
      params.require(:message).permit(:recipient_id, :body)
    end

    def message_update_params
      params.require(:message).permit(:body, :read_at)
    end
  end
end
