module Mutations
  class SendMessage < Mutations::BaseMutation
    argument :recipient_id, ID, required: true
    argument :body, String, required: true

    field :message, Types::MessageType, null: true
    field :errors, [String], null: false

    def resolve(recipient_id:, body:)
      authenticate_user!
      result = Messages::CreateMessage.call(
        sender: current_user,
        attributes: { recipient_id: recipient_id, body: body }
      )

      { message: result.message, errors: result.errors }
    end
  end
end
