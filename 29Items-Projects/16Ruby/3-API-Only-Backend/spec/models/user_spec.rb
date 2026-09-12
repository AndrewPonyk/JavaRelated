# frozen_string_literal: true

require 'rails_helper'

RSpec.describe User, type: :model do
  it 'normalizes email and authenticates with a secure password' do
    user = create(:user, email: ' Person@Example.COM ')

    expect(user.email).to eq('person@example.com')
    expect(user.authenticate('password123')).to eq(user)
  end

  it 'requires a valid unique email' do
    create(:user, email: 'taken@example.com')
    user = build(:user, email: 'TAKEN@example.com')

    expect(user).not_to be_valid
    expect(user.errors[:email]).to be_present
  end

  it 'requires a minimum password length' do
    user = build(:user, password: 'short', password_confirmation: 'short')

    expect(user).not_to be_valid
    expect(user.errors[:password]).to be_present
  end
end
