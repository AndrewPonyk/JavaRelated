class TimeEntry < ApplicationRecord
  belongs_to :issue
  belongs_to :user

  validates :hours, presence: true,
                    numericality: { greater_than: 0, less_than_or_equal_to: 24 }
  validates :worked_on, presence: true
end
