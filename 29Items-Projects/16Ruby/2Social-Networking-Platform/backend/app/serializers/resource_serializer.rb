class ResourceSerializer
  class << self
    def user(user)
      {
        id: user.id,
        email: user.email,
        username: user.username,
        display_name: user.display_name,
        created_at: user.created_at
      }
    end

    def post(post)
      {
        id: post.id,
        user_id: post.user_id,
        body: post.body,
        visibility: post.visibility,
        reply_to_post_id: post.reply_to_post_id,
        toxicity_score: post.toxicity_score&.to_f,
        created_at: post.created_at,
        updated_at: post.updated_at
      }
    end

    def follow(follow)
      {
        id: follow.id,
        follower_id: follow.follower_id,
        followee_id: follow.followee_id,
        notifications_enabled: follow.notifications_enabled,
        created_at: follow.created_at
      }
    end

    def message(message)
      {
        id: message.id,
        sender_id: message.sender_id,
        recipient_id: message.recipient_id,
        body: message.body,
        read_at: message.read_at,
        toxicity_score: message.toxicity_result&.score&.to_f,
        created_at: message.created_at,
        updated_at: message.updated_at
      }
    end

    def notification(notification)
      {
        id: notification.id,
        user_id: notification.user_id,
        kind: notification.kind,
        payload: notification.payload,
        read_at: notification.read_at,
        created_at: notification.created_at
      }
    end

    def toxicity_result(result)
      {
        id: result.id,
        post_id: result.post_id,
        message_id: result.message_id,
        score: result.score.to_f,
        label: result.label,
        model_version: result.model_version,
        created_at: result.created_at
      }
    end
  end
end
