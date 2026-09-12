# frozen_string_literal: true

require 'rails_helper'

RSpec.describe 'Users API', type: :request do
  it 'returns the current user profile' do
    user = create(:user)

    get "/api/v1/users/#{user.id}", headers: auth_headers(user)

    expect(response).to have_http_status(:ok)
    expect(json.dig('user', 'id')).to eq(user.id)
  end

  it 'updates the current user' do
    user = create(:user)

    patch "/api/v1/users/#{user.id}",
          params: { user: { email: 'updated@example.com' } }.to_json,
          headers: auth_headers(user)

    expect(response).to have_http_status(:ok)
    expect(json.dig('user', 'email')).to eq('updated@example.com')
  end

  it 'prevents access to another user record' do
    user = create(:user)
    other = create(:user)

    get "/api/v1/users/#{other.id}", headers: auth_headers(user)

    expect(response).to have_http_status(:forbidden)
  end

  it 'returns a JSON 404 for missing user records' do
    user = create(:user)

    get '/api/v1/users/00000000-0000-0000-0000-000000000000', headers: auth_headers(user)

    expect(response).to have_http_status(:not_found)
    expect(json.dig('error', 'code')).to eq('not_found')
  end
end
