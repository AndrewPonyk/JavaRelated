require "rails_helper"

RSpec.describe "GraphQL controller", type: :request do
  it "passes JSON variables through to GraphQL execution" do
    create(:user, email: "demo@example.com", username: "demo_user", password: "password123")

    post "/graphql",
         params: {
           query: <<~GRAPHQL,
             mutation Login($email: String!, $password: String!) {
               loginUser(email: $email, password: $password) {
                 authPayload { accessToken user { username } }
                 errors
               }
             }
           GRAPHQL
           variables: { email: "demo@example.com", password: "password123" }
         }.to_json,
         headers: { "Content-Type" => "application/json" }

    expect(response).to have_http_status(:ok)
    expect(json_body.dig("data", "loginUser", "authPayload", "user", "username")).to eq("demo_user")
    expect(json_body.dig("data", "loginUser", "errors")).to eq([])
  end
end
