class TimeEntryPolicy < ApplicationPolicy
  def create?
    record.issue.project.users.exists?(id: user.id)
  end

  def destroy?
    record.user_id == user.id || user.admin_for?(record.issue.project)
  end

  class Scope < ApplicationPolicy::Scope
    def resolve
      scope.where(user_id: user.id)
    end
  end
end
