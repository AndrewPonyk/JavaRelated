# frozen_string_literal: true

require 'rails_helper'

RSpec.describe AuthTokenService do
  it 'issues and decodes a signed token' do
    user = create(:user)
    token = described_class.issue(user).fetch(:token)

    payload = described_class.decode(token)

    expect(payload.fetch('sub')).to eq(user.id)
    expect(payload.fetch('jti')).to be_present
  end

  it 'treats revoked tokens as revoked' do
    user = create(:user)
    token = described_class.issue(user).fetch(:token)
    payload = described_class.decode(token)

    described_class.revoke!(payload.fetch('jti'), Time.zone.at(payload.fetch('exp')))

    expect(described_class.revoked?(payload.fetch('jti'))).to be(true)
  end
end
