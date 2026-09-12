class WorkloadPrediction < ApplicationRecord
  belongs_to :sprint

  validates :predicted_hours, numericality: { greater_than_or_equal_to: 0 }
  validates :confidence, numericality: { in: 0.0..1.0 }
  validates :model_version, presence: true
end
