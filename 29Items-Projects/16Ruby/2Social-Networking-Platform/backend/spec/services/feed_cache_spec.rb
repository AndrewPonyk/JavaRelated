require "rails_helper"

RSpec.describe FeedCache do
  let(:sets) { Hash.new { |hash, key| hash[key] = [] } }
  let(:redis) do
    store = sets
    Object.new.tap do |client|
      client.define_singleton_method(:zadd) do |key, score, value|
        store[key] << [score, value.to_s]
      end

      client.define_singleton_method(:expire) { |_key, _ttl| true }

      client.define_singleton_method(:zrevrange) do |key, start_index, end_index|
        store[key].sort_by(&:first).reverse.map(&:last)[start_index..end_index] || []
      end
    end
  end

  it "fans posts out to author and follower feeds" do
    author = create(:user)
    follower = create(:user)
    create(:follow, follower: follower, followee: author)
    post = create(:post, user: author)

    described_class.new(author, redis: redis).fanout_post(post)

    expect(described_class.new(author, redis: redis).recent_posts(limit: 10)).to include(post)
    expect(described_class.new(follower, redis: redis).recent_posts(limit: 10)).to include(post)
  end
end
