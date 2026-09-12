module Mutations
  class MarkNotificationRead < Mutations::BaseMutation
    argument :id, ID, required: true

    field :notification, Types::NotificationType, null: true

    def resolve(id:)
      authenticate_user!
      notification = current_user.notifications.find(id)
      notification.mark_read!
      { notification: notification }
    end
  end
end
