require "rails_helper"

RSpec.describe SearchIndexer do
  it "falls back to PostgreSQL search and respects viewer visibility" do
    author = create(:user, username: "author_user")
    viewer = create(:user)
    create(:follow, follower: viewer, followee: author)
    visible_post = create(:post, user: author, body: "needle public post", visibility: "followers")
    hidden_post = create(:post, user: create(:user), body: "needle private post", visibility: "private")

    indexer = described_class.new(endpoint: "http://127.0.0.1:1")
    results = indexer.search_posts("needle", viewer: viewer, limit: 20)

    expect(results).to include(visible_post)
    expect(results).not_to include(hidden_post)
  end

  it "does not return every post for a blank query" do
    create(:post, body: "anything")

    expect(described_class.new.search_posts(" ", viewer: nil)).to be_empty
  end
end
