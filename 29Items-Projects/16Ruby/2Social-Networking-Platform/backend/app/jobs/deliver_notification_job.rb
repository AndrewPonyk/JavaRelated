class DeliverNotificationJob < ApplicationJob
  queue_as :notifications

  def perform(user_id, kind, payload = {})
    user = User.find(user_id)
    user.notifications.create!(kind: kind, payload: payload)
  end
end
