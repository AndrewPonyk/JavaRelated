class SprintPolicy < ApplicationPolicy
  def index? = member?
  def show? = member?
  def create? = admin?
  def update? = admin?
  def destroy? = admin?

  private

  def member?
    record.project.users.exists?(id: user.id)
  end

  def admin?
    user.admin_for?(record.project) || user.admin?
  end

  class Scope < ApplicationPolicy::Scope
    def resolve
      scope.joins(project: :memberships)
           .where(memberships: { user_id: user.id }).distinct
    end
  end
end
