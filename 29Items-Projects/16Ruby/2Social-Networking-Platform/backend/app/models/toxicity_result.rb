class ToxicityResult < ApplicationRecord
  belongs_to :post, optional: true
  belongs_to :message, optional: true

  validates :score, numericality: { greater_than_or_equal_to: 0, less_than_or_equal_to: 1 }
  validates :label, presence: true
  validates :model_version, presence: true
  validate :exactly_one_target

  private

  def exactly_one_target
    return if [post_id.present?, message_id.present?].count(true) == 1

    errors.add(:base, "toxicity result must belong to exactly one target")
  end
end
