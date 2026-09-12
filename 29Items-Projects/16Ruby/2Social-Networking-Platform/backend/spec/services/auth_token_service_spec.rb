require "rails_helper"

RSpec.describe Auth::TokenService do
  it "round-trips valid tokens and rejects tampered tokens" do
    user = create(:user)
    token = described_class.access_token_for(user)

    expect(described_class.user_from_token(token)).to eq(user)
    expect(described_class.user_from_token("#{token}x")).to be_nil
  end
end
