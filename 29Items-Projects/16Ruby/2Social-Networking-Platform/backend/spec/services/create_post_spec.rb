require "rails_helper"

RSpec.describe Posts::CreatePost do
  it "persists the post, stores toxicity metadata, and enqueues async work" do
    user = create(:user)

    result = nil
    expect do
      result = described_class.call(user: user, attributes: { body: "hello world", visibility: "public" })
    end.to change(Post, :count).by(1).and change(ToxicityResult, :count).by(1)

    expect(result).to be_success
    expect(result.post.toxicity_result.label).to eq("safe")
    expect(IndexPostJob).to have_been_enqueued
  end

  it "returns validation errors" do
    result = described_class.call(user: create(:user), attributes: { body: "", visibility: "public" })

    expect(result).not_to be_success
    expect(result.errors).to include("Body can't be blank")
  end
end
