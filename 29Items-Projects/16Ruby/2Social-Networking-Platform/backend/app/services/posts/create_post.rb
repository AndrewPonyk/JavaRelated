module Posts
  class CreatePost
    def self.call(user:, attributes:)
      new(user: user, attributes: attributes).call
    end

    def initialize(user:, attributes:)
      @user = user
      @attributes = attributes
    end

    def call
      return ApplicationResult.new(false, nil, ["Authentication required"]) unless user

      post = user.posts.build(attributes)
      toxicity = ToxicityClassifier.new.classify(text: post.body.to_s)

      Post.transaction do
        post.save!
        post.create_toxicity_result!(
          score: toxicity.fetch(:score),
          label: toxicity.fetch(:label),
          model_version: toxicity.fetch(:model_version)
        )
      end

      FeedCache.new(user).fanout_post(post)
      IndexPostJob.perform_later(post.id)
      DeliverNotificationJob.perform_later(user.id, "post_created", { "post_id" => post.id })

      ApplicationResult.new(true, post, [])
    rescue ActiveRecord::RecordInvalid => e
      ApplicationResult.new(false, post, e.record.errors.full_messages)
    end

    private

    attr_reader :user, :attributes
  end
end
