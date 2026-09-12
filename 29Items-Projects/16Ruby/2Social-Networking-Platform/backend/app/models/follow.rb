class Follow < ApplicationRecord
  belongs_to :follower, class_name: "User"
  belongs_to :followee, class_name: "User"

  validates :follower_id, uniqueness: { scope: :followee_id }
  validate :cannot_follow_self

  after_create_commit :notify_followee

  private

  def cannot_follow_self
    errors.add(:followee_id, "cannot be the same as follower") if follower_id == followee_id
  end

  def notify_followee
    DeliverNotificationJob.perform_later(followee_id, "follow", { follower_id: follower_id })
  end
end
