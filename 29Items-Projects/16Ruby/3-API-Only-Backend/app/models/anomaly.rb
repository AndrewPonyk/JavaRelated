# frozen_string_literal: true

class Anomaly < ApplicationRecord
  SEVERITIES = %w[low medium high critical].freeze

  validates :metric_name, :severity, :description, :detected_at, :observed_value, presence: true
  validates :severity, inclusion: { in: SEVERITIES }
  validates :baseline_value, :observed_value, numericality: true

  scope :open, -> { where(resolved_at: nil) }
  scope :recent, -> { order(detected_at: :desc) }

  before_validation :ensure_detected_at

  def resolved?
    resolved_at.present?
  end

  def resolve!
    update!(resolved_at: Time.current)
  end

  def serializable_hash(options = nil)
    super(
      {
        only: %i[id metric_name severity description baseline_value observed_value detected_at resolved_at created_at
                 updated_at],
        methods: %i[resolved?]
      }.merge(options || {})
    )
  end

  private

  def ensure_detected_at
    self.detected_at ||= Time.current
  end
end
