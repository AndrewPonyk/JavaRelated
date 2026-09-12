require "rails_helper"

RSpec.describe "health query" do
  it "returns ok" do
    result = SocialNetworkSchema.execute("{ health }")

    expect(result.dig("data", "health")).to eq("ok")
  end

  it "runs core product mutations and queries" do
    other = create(:user)
    registration = SocialNetworkSchema.execute(
      <<~GRAPHQL
        mutation {
          registerUser(email: "gql@example.com", username: "gql_user", password: "password123") {
            authPayload { accessToken user { id username } }
            errors
          }
        }
      GRAPHQL
    )
    user = User.find_by!(username: "gql_user")

    create_post = SocialNetworkSchema.execute(
      <<~GRAPHQL,
        mutation {
          createPost(body: "GraphQL post", visibility: "public") {
            post { id body toxicityScore }
            errors
          }
        }
      GRAPHQL
      context: { current_user: user }
    )

    follow = SocialNetworkSchema.execute(
      "mutation($id: ID!) { followUser(followeeId: $id) { follow { id } errors } }",
      variables: { id: other.id },
      context: { current_user: user }
    )

    message = SocialNetworkSchema.execute(
      "mutation($id: ID!) { sendMessage(recipientId: $id, body: \"hello\") { message { id body } errors } }",
      variables: { id: other.id },
      context: { current_user: user }
    )

    feed = SocialNetworkSchema.execute("{ feed { id body } }", context: { current_user: user })

    expect(registration.dig("data", "registerUser", "errors")).to eq([])
    expect(create_post.dig("data", "createPost", "post", "body")).to eq("GraphQL post")
    expect(follow.dig("data", "followUser", "errors")).to eq([])
    expect(message.dig("data", "sendMessage", "errors")).to eq([])
    expect(feed.dig("data", "feed").first.fetch("body")).to eq("GraphQL post")
  end
end
