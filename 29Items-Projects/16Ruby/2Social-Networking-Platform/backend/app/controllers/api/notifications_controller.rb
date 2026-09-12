module Api
  class NotificationsController < ApplicationController
    before_action :authenticate_user!

    def index
      notifications = current_user.notifications
                                  .order(created_at: :desc)
                                  .limit(bounded_limit(default: 50, maximum: 100))
      render json: notifications.map { |notification| ResourceSerializer.notification(notification) }
    end

    def show
      render json: ResourceSerializer.notification(current_user.notifications.find(params[:id]))
    end

    def create
      notification = current_user.notifications.create!(notification_params)
      render json: ResourceSerializer.notification(notification), status: :created
    end

    def update
      notification = current_user.notifications.find(params[:id])
      notification.update!(notification_params)
      render json: ResourceSerializer.notification(notification)
    end

    def destroy
      current_user.notifications.find(params[:id]).destroy!
      head :no_content
    end

    private

    def notification_params
      params.require(:notification).permit(:kind, :read_at, payload: {})
    end
  end
end
