require "rails_helper"

RSpec.describe Messages::CreateMessage do
  it "creates a message, toxicity result, and notification job" do
    sender = create(:user)
    recipient = create(:user)

    result = nil
    expect do
      result = described_class.call(sender: sender, attributes: { recipient_id: recipient.id, body: "hello" })
    end.to change(Message, :count).by(1).and change(ToxicityResult, :count).by(1)

    expect(result).to be_success
    expect(result.message).to be_a(Message)
    expect(DeliverNotificationJob).to have_been_enqueued
  end

  it "returns validation errors for blank messages" do
    result = described_class.call(
      sender: create(:user),
      attributes: { recipient_id: create(:user).id, body: "" }
    )

    expect(result).not_to be_success
    expect(result.errors).to include("Body can't be blank")
  end
end
