require "rails_helper"

RSpec.describe "REST CRUD endpoints", type: :request do
  let(:user) { create(:user) }
  let(:other) { create(:user) }

  it "supports user CRUD" do
    get "/api/users"
    expect(response).to have_http_status(:ok)

    post "/api/users",
         params: {
           user: {
             email: "created@example.com",
             username: "created_user",
             password: "password123",
             password_confirmation: "password123"
           }
         }.to_json,
         headers: { "Content-Type" => "application/json" }
    expect(response).to have_http_status(:created)

    patch "/api/users/#{user.id}",
          params: { user: { display_name: "Updated User" } }.to_json,
          headers: auth_headers(user)
    expect(response).to have_http_status(:ok)
    expect(json_body.fetch("display_name")).to eq("Updated User")

    delete "/api/users/#{user.id}", headers: auth_headers(user)
    expect(response).to have_http_status(:no_content)
  end

  it "supports posts CRUD with ownership checks" do
    post "/api/posts",
         params: { post: { body: "first post", visibility: "public" } }.to_json,
         headers: auth_headers(user)
    expect(response).to have_http_status(:created)
    post_id = json_body.fetch("id")

    get "/api/posts/#{post_id}"
    expect(response).to have_http_status(:ok)

    patch "/api/posts/#{post_id}",
          params: { post: { body: "updated post" } }.to_json,
          headers: auth_headers(user)
    expect(response).to have_http_status(:ok)
    expect(json_body.fetch("body")).to eq("updated post")

    delete "/api/posts/#{post_id}", headers: auth_headers(user)
    expect(response).to have_http_status(:no_content)
  end

  it "supports follows CRUD" do
    post "/api/follows",
         params: { follow: { followee_id: other.id } }.to_json,
         headers: auth_headers(user)
    expect(response).to have_http_status(:created)
    follow_id = json_body.fetch("id")

    get "/api/follows/#{follow_id}", headers: auth_headers(user)
    expect(response).to have_http_status(:ok)

    patch "/api/follows/#{follow_id}",
          params: { follow: { notifications_enabled: false } }.to_json,
          headers: auth_headers(user)
    expect(response).to have_http_status(:ok)
    expect(json_body.fetch("notifications_enabled")).to be(false)

    delete "/api/follows/#{follow_id}", headers: auth_headers(user)
    expect(response).to have_http_status(:no_content)
  end

  it "supports messages, notifications, and toxicity result CRUD" do
    post "/api/messages",
         params: { message: { recipient_id: other.id, body: "private hello" } }.to_json,
         headers: auth_headers(user)
    expect(response).to have_http_status(:created)
    message_id = json_body.fetch("id")

    get "/api/messages/#{message_id}", headers: auth_headers(user)
    expect(response).to have_http_status(:ok)

    patch "/api/messages/#{message_id}",
          params: { message: { body: "edited hello" } }.to_json,
          headers: auth_headers(user)
    expect(response).to have_http_status(:ok)

    post "/api/notifications",
         params: { notification: { kind: "message", payload: { message_id: message_id } } }.to_json,
         headers: auth_headers(user)
    expect(response).to have_http_status(:created)
    notification_id = json_body.fetch("id")

    get "/api/notifications/#{notification_id}", headers: auth_headers(user)
    expect(response).to have_http_status(:ok)

    patch "/api/notifications/#{notification_id}",
          params: { notification: { read_at: Time.current.iso8601 } }.to_json,
          headers: auth_headers(user)
    expect(response).to have_http_status(:ok)

    get "/api/search?q=edited", headers: auth_headers(user)
    expect(response).to have_http_status(:ok)

    toxicity_result = ToxicityResult.find_by!(message_id: message_id)
    get "/api/toxicity_results/#{toxicity_result.id}", headers: auth_headers(user)
    expect(response).to have_http_status(:ok)

    patch "/api/toxicity_results/#{toxicity_result.id}",
          params: { toxicity_result: { label: "review", score: 0.5 } }.to_json,
          headers: auth_headers(user)
    expect(response).to have_http_status(:ok)

    delete "/api/notifications/#{notification_id}", headers: auth_headers(user)
    expect(response).to have_http_status(:no_content)

    delete "/api/toxicity_results/#{toxicity_result.id}", headers: auth_headers(user)
    expect(response).to have_http_status(:no_content)
  end
end
