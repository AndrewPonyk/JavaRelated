class Issue < ApplicationRecord
  include PgSearch::Model

  STATUSES = %w[backlog todo in_progress review done].freeze
  PRIORITIES = %w[low medium high urgent].freeze

  belongs_to :project
  belongs_to :sprint, optional: true
  belongs_to :reporter, class_name: "User"
  belongs_to :assignee, class_name: "User", optional: true
  has_many :time_entries, dependent: :destroy
  has_many :comments, dependent: :destroy

  validates :title, presence: true, length: { maximum: 255 }
  validates :status, inclusion: { in: STATUSES }
  validates :priority, inclusion: { in: PRIORITIES }
  validates :estimate_hours, numericality: { greater_than_or_equal_to: 0, allow_nil: true }

  scope :open, -> { where.not(status: "done") }
  scope :in_sprint, ->(sprint) { where(sprint_id: sprint.id) }

  pg_search_scope :search_text, against: %i[title description],
                                using: { tsearch: { prefix: true } }

  after_update_commit :broadcast_board_update

  def logged_hours
    time_entries.sum(:hours)
  end

  private

  def broadcast_board_update
    return unless saved_change_to_status? || saved_change_to_assignee_id?

    broadcast_replace_to(
      [project, :board],
      partial: "issues/card",
      locals: { issue: self }
    )
  end
end
