class BoardsController < ApplicationController
  def show
    @project = Project.find(params[:project_id])
    authorize @project, :show?

    issues = policy_scope(Issue).where(project: @project)
                                .includes(:assignee, :sprint)
                                .order(:updated_at)
    grouped = issues.group_by(&:status)
    @columns = Issue::STATUSES.index_with { |status| grouped.fetch(status, []) }
  end
end
