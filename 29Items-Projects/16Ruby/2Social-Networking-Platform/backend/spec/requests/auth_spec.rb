require "rails_helper"

RSpec.describe "authentication", type: :request do
  it "registers, returns tokens, and exposes the current user" do
    post "/api/auth/register",
         params: {
           user: {
             email: "new@example.com",
             username: "new_user",
             display_name: "New User",
             password: "password123",
             password_confirmation: "password123"
           }
         }.to_json,
         headers: { "Content-Type" => "application/json" }

    expect(response).to have_http_status(:created)
    token = json_body.fetch("access_token")

    get "/api/auth/me", headers: { "Authorization" => "Bearer #{token}" }
    expect(response).to have_http_status(:ok)
    expect(json_body.dig("user", "username")).to eq("new_user")
  end

  it "rejects invalid credentials" do
    create(:user, email: "demo@example.com", password: "password123")

    post "/api/auth/login",
         params: { email: "demo@example.com", password: "wrong-password" }.to_json,
         headers: { "Content-Type" => "application/json" }

    expect(response).to have_http_status(:unauthorized)
  end

  it "logs in, refreshes, and logs out a session" do
    user = create(:user, email: "demo@example.com", password: "password123")

    post "/api/auth/login",
         params: { email: "demo@example.com", password: "password123" }.to_json,
         headers: { "Content-Type" => "application/json" }

    expect(response).to have_http_status(:ok)
    access_token = json_body.fetch("access_token")
    refresh_token = json_body.fetch("refresh_token")

    post "/api/auth/refresh",
         params: { user_id: user.id, refresh_token: refresh_token }.to_json,
         headers: { "Content-Type" => "application/json" }
    expect(response).to have_http_status(:ok)
    expect(json_body.fetch("refresh_token")).not_to eq(refresh_token)

    delete "/api/auth/logout", headers: { "Authorization" => "Bearer #{access_token}" }
    expect(response).to have_http_status(:no_content)
  end
end
