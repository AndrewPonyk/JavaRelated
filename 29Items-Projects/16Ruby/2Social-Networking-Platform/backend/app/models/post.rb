class Post < ApplicationRecord
  VISIBILITIES = %w[public followers private].freeze

  belongs_to :user
  has_one :toxicity_result, dependent: :destroy
  belongs_to :reply_to_post, class_name: "Post", optional: true
  has_many :replies, class_name: "Post", foreign_key: :reply_to_post_id, dependent: :nullify, inverse_of: :reply_to_post

  validates :body, presence: true, length: { maximum: 2_000 }
  validates :visibility, inclusion: { in: VISIBILITIES }

  delegate :score, to: :toxicity_result, prefix: :toxicity, allow_nil: true

  scope :visible_to, lambda { |viewer|
    return where(visibility: "public") unless viewer

    followed_ids = Follow.where(follower_id: viewer.id).select(:followee_id)
    where(visibility: "public")
      .or(where(user_id: viewer.id))
      .or(where(visibility: "followers", user_id: followed_ids))
  }
end
