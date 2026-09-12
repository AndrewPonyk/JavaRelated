class IssuePolicy < ApplicationPolicy
  def index? = member?
  def show? = member?
  def create? = member?
  def update? = member?
  def destroy? = member? && (admin? || record.reporter_id == user.id)

  private

  def member?
    record.project.users.exists?(id: user.id)
  end

  def admin?
    record.project.memberships.find_by(user_id: user.id)&.role == "admin"
  end

  class Scope < ApplicationPolicy::Scope
    def resolve
      scope.joins(project: :memberships).where(memberships: { user_id: user.id }).distinct
    end
  end
end
