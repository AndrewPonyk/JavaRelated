class ProjectPolicy < ApplicationPolicy
  def index? = true
  def show? = member?
  def create? = true
  def update? = admin?
  def destroy? = admin?

  private

  def member?
    record.users.exists?(id: user.id)
  end

  def admin?
    record.memberships.find_by(user_id: user.id)&.admin? || user.admin?
  end

  class Scope < ApplicationPolicy::Scope
    def resolve
      scope.joins(:memberships).where(memberships: { user_id: user.id }).distinct
    end
  end
end
