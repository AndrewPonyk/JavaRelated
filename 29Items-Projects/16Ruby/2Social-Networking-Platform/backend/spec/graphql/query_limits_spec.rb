require "rails_helper"

RSpec.describe "GraphQL query limits" do
  it "clamps list limits to the configured maximum" do
    create_list(:user, 3)

    result = SocialNetworkSchema.execute("{ users(limit: 999) { id } }")

    expect(result.dig("data", "users").size).to eq(3)
  end

  it "rejects blank search terms" do
    result = SocialNetworkSchema.execute('{ searchPosts(query: "   ") { id } }')

    expect(result.fetch("errors").first.fetch("message")).to eq("Search query cannot be blank")
  end
end
