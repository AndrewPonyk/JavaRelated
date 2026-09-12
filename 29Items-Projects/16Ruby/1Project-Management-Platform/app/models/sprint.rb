class Sprint < ApplicationRecord
  STATES = %w[planned active completed].freeze

  belongs_to :project
  has_many :issues, dependent: :nullify
  has_many :workload_predictions, dependent: :destroy

  validates :name, presence: true
  validates :starts_on, :ends_on, presence: true
  validates :state, inclusion: { in: STATES }
  validate :end_after_start

  scope :active, -> { where(state: "active") }

  def total_estimate
    issues.sum(:estimate_hours)
  end

  def completion_ratio
    return 0 if issues.empty?

    issues.where(status: "done").count.to_f / issues.count
  end

  private

  def end_after_start
    return if starts_on.blank? || ends_on.blank?

    errors.add(:ends_on, "must be after starts_on") if ends_on <= starts_on
  end
end
