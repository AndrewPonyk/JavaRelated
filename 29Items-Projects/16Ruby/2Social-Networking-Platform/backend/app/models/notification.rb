class Notification < ApplicationRecord
  KINDS = %w[follow mention reply message moderation post_created].freeze

  belongs_to :user

  validates :kind, inclusion: { in: KINDS }

  scope :unread, -> { where(read_at: nil) }

  def mark_read!
    update!(read_at: Time.current)
  end
end
