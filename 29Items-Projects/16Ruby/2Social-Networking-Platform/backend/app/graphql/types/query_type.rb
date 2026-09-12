module Types
  class QueryType < Types::BaseObject
    field :health, String, null: false
    field :me, Types::UserType, null: true
    field :users, [Types::UserType], null: false do
      argument :limit, Integer, required: false, default_value: 50
    end
    field :feed, [Types::PostType], null: false do
      argument :limit, Integer, required: false, default_value: 25
    end
    field :posts, [Types::PostType], null: false do
      argument :limit, Integer, required: false, default_value: 50
    end
    field :messages, [Types::MessageType], null: false do
      argument :limit, Integer, required: false, default_value: 50
    end
    field :notifications, [Types::NotificationType], null: false do
      argument :limit, Integer, required: false, default_value: 50
    end
    field :search_posts, [Types::PostType], null: false do
      argument :query, String, required: true
      argument :limit, Integer, required: false, default_value: 20
    end

    def health
      "ok"
    end

    def me
      context[:current_user]
    end

    def users(limit:)
      User.order(:username).limit(bounded_limit(limit, maximum: 100))
    end

    def feed(limit:)
      user = context[:current_user]
      FeedCache.new(user).recent_posts(limit: bounded_limit(limit, maximum: 100))
    end

    def posts(limit:)
      Post.visible_to(context[:current_user])
          .includes(:user, :toxicity_result)
          .order(created_at: :desc)
          .limit(bounded_limit(limit, maximum: 100))
    end

    def messages(limit:)
      user = context[:current_user] || raise(GraphQL::ExecutionError, "Authentication required")
      Message.visible_to(user)
             .includes(:sender, :recipient, :toxicity_result)
             .order(created_at: :desc)
             .limit(bounded_limit(limit, maximum: 100))
    end

    def notifications(limit:)
      user = context[:current_user] || raise(GraphQL::ExecutionError, "Authentication required")
      user.notifications.order(created_at: :desc).limit(bounded_limit(limit, maximum: 100))
    end

    def search_posts(query:, limit:)
      normalized_query = query.to_s.strip
      raise GraphQL::ExecutionError, "Search query cannot be blank" if normalized_query.blank?

      SearchIndexer.new.search_posts(
        normalized_query,
        viewer: context[:current_user],
        limit: bounded_limit(limit, maximum: 50)
      )
    end

    private

    def bounded_limit(value, maximum:)
      [[value.to_i, 1].max, maximum].min
    end
  end
end
