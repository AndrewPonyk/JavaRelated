class Message < ApplicationRecord
  belongs_to :sender, class_name: "User"
  belongs_to :recipient, class_name: "User"
  has_one :toxicity_result, dependent: :destroy

  validates :body, presence: true, length: { maximum: 5_000 }

  scope :visible_to, ->(user) { where(sender_id: user.id).or(where(recipient_id: user.id)) }

  delegate :score, to: :toxicity_result, prefix: :toxicity, allow_nil: true
end
