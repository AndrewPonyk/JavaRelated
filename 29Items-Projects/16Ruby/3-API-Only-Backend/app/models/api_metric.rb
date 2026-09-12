# frozen_string_literal: true

class ApiMetric < ApplicationRecord
  HTTP_METHODS = %w[DELETE GET HEAD OPTIONS PATCH POST PUT].freeze

  belongs_to :user, optional: true

  validates :method, :path, :status, :latency_ms, :occurred_at, presence: true
  validates :method, inclusion: { in: HTTP_METHODS }
  validates :path, format: { with: %r{\A/} }
  validates :status, numericality: { only_integer: true, greater_than_or_equal_to: 100, less_than: 600 }
  validates :latency_ms, numericality: { greater_than_or_equal_to: 0 }
  validate :metadata_must_be_object

  scope :recent, -> { order(occurred_at: :desc) }
  scope :errors, -> { where(status: 500..) }

  before_validation :ensure_defaults

  def serializable_hash(options = nil)
    super(
      {
        only: %i[id user_id method path status latency_ms occurred_at metadata created_at updated_at]
      }.merge(options || {})
    )
  end

  private

  def ensure_defaults
    self.occurred_at ||= Time.current
    self.metadata ||= {}
  end

  def metadata_must_be_object
    errors.add(:metadata, 'must be an object') unless metadata.is_a?(Hash)
  end
end
