# frozen_string_literal: true

require 'rails_helper'

RSpec.describe 'Authentication API', type: :request do
  it 'registers a user and returns a bearer token' do
    post '/api/v1/auth/register',
         params: { user: { email: 'new@example.com', password: 'password123', password_confirmation: 'password123' } },
         as: :json

    expect(response).to have_http_status(:created)
    expect(json.fetch('token')).to be_present
    expect(json.dig('user', 'email')).to eq('new@example.com')
  end

  it 'logs a user in and out' do
    user = create(:user, email: 'login@example.com', password: 'password123', password_confirmation: 'password123')

    post '/api/v1/auth/login', params: { user: { email: user.email, password: 'password123' } }, as: :json
    expect(response).to have_http_status(:ok)

    token = json.fetch('token')
    delete '/api/v1/auth/logout', headers: { 'Authorization' => "Bearer #{token}" }

    expect(response).to have_http_status(:ok)
    expect(RevokedToken.count).to eq(1)
  end

  it 'rejects invalid credentials' do
    create(:user, email: 'login@example.com', password: 'password123', password_confirmation: 'password123')

    post '/api/v1/auth/login', params: { user: { email: 'login@example.com', password: 'wrongpass' } }, as: :json

    expect(response).to have_http_status(:unauthorized)
    expect(json.dig('error', 'code')).to eq('unauthorized')
  end

  it 'rejects authenticated endpoints without a bearer token' do
    get '/api/v1/sync/status'

    expect(response).to have_http_status(:unauthorized)
    expect(json.dig('error', 'code')).to eq('unauthorized')
  end
end
