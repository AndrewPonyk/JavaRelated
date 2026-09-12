require "rails_helper"

RSpec.describe "API error handling", type: :request do
  it "returns 400 for missing required parameters" do
    post "/api/auth/login",
         params: { email: "demo@example.com" }.to_json,
         headers: { "Content-Type" => "application/json" }

    expect(response).to have_http_status(:bad_request)
    expect(json_body.fetch("error")).to include("password")
  end

  it "returns 404 for missing records" do
    get "/api/posts/999999"

    expect(response).to have_http_status(:not_found)
  end

  it "returns 403 for ownership violations" do
    owner = create(:user)
    other = create(:user)
    post = create(:post, user: owner)

    patch "/api/posts/#{post.id}",
          params: { post: { body: "not allowed" } }.to_json,
          headers: auth_headers(other)

    expect(response).to have_http_status(:not_found)
  end

  it "rejects blank search queries" do
    get "/api/search?q=   "

    expect(response).to have_http_status(:bad_request)
    expect(json_body.fetch("error")).to eq("Search query cannot be blank")
  end
end
