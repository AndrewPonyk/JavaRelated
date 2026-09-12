class Comment < ApplicationRecord
  belongs_to :issue
  belongs_to :user

  validates :body, presence: true, length: { maximum: 5_000 }

  after_create_commit :broadcast_append

  private

  def broadcast_append
    broadcast_append_to(
      [issue, :comments],
      target: "issue_#{issue.id}_comments",
      partial: "comments/comment",
      locals: { comment: self }
    )
  end
end
