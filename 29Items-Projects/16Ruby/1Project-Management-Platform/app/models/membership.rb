class Membership < ApplicationRecord
  ROLES = %w[admin member viewer].freeze

  belongs_to :user
  belongs_to :project

  validates :role, inclusion: { in: ROLES }
  validates :user_id, uniqueness: { scope: :project_id }

  def admin?  = role == "admin"
  def member? = role == "member"
  def viewer? = role == "viewer"
end
