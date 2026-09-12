module Messages
  class CreateMessage
    def self.call(sender:, attributes:)
      new(sender: sender, attributes: attributes).call
    end

    def initialize(sender:, attributes:)
      @sender = sender
      @attributes = attributes
    end

    def call
      return ApplicationResult.new(false, nil, ["Authentication required"]) unless sender

      message = sender.messages.build(attributes)
      toxicity = ToxicityClassifier.new.classify(text: message.body.to_s)

      Message.transaction do
        message.save!
        message.create_toxicity_result!(
          score: toxicity.fetch(:score),
          label: toxicity.fetch(:label),
          model_version: toxicity.fetch(:model_version)
        )
      end

      DeliverNotificationJob.perform_later(
        message.recipient_id,
        "message",
        { "message_id" => message.id, "sender_id" => sender.id }
      )

      ApplicationResult.new(true, message, [])
    rescue ActiveRecord::RecordInvalid => e
      ApplicationResult.new(false, message, e.record.errors.full_messages)
    end

    private

    attr_reader :sender, :attributes
  end
end
