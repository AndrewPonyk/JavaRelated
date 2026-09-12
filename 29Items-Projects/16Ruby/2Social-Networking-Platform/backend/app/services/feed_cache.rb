class FeedCache
  FEED_TTL = 6.hours.to_i

  def initialize(user, redis: REDIS)
    @user = user
    @redis = redis
  end

  def recent_posts(limit:)
    ids = redis.zrevrange(feed_key(user.id), 0, limit - 1) if user
    if ids.present?
      return Post.visible_to(user)
                 .includes(:user, :toxicity_result)
                 .where(id: ids)
                 .order(created_at: :desc)
    end

    fallback_posts(limit)
  rescue Redis::BaseError
    fallback_posts(limit)
  end

  def fanout_post(post)
    follower_ids = user.follower_relationships.pluck(:follower_id)
    ([user.id] + follower_ids).each do |target_user_id|
      key = feed_key(target_user_id)
      redis.zadd(key, post.created_at.to_i, post.id)
      redis.expire(key, FEED_TTL)
    end
  rescue Redis::BaseError => e
    Rails.logger.warn(event: "feed_cache.fanout_failed", post_id: post.id, error: e.message)
  end

  private

  attr_reader :user, :redis

  def feed_key(user_id)
    "feeds:users:#{user_id}:posts"
  end

  def fallback_posts(limit)
    Post.visible_to(user)
        .includes(:user, :toxicity_result)
        .order(created_at: :desc)
        .limit(limit)
  end
end
