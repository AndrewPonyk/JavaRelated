class IssuesController < ApplicationController
  before_action :set_project
  before_action :set_issue, only: %i[show edit update destroy]

  def index
    @q = params[:q].to_s.strip
    scope = policy_scope(@project.issues).includes(:assignee, :sprint)
    scope = scope.search_text(@q) if @q.present?
    @issues = scope.order(updated_at: :desc).limit(100)
  end

  def show
    authorize @issue
    @comment = Comment.new
    @time_entry = TimeEntry.new
  end

  def new
    @issue = @project.issues.build(priority: "medium", status: "backlog")
    authorize @issue
  end

  def create
    @issue = @project.issues.build(issue_params.merge(reporter: current_user))
    authorize @issue

    if @issue.save
      respond_to do |format|
        format.html { redirect_to project_issue_path(@project, @issue), notice: "Issue created." }
        format.turbo_stream
      end
    else
      render :new, status: :unprocessable_entity
    end
  end

  def edit
    authorize @issue
  end

  def update
    authorize @issue

    if @issue.update(issue_params)
      respond_to do |format|
        format.html { redirect_to project_issue_path(@project, @issue), notice: "Issue updated." }
        format.turbo_stream
      end
    else
      render :edit, status: :unprocessable_entity
    end
  end

  def destroy
    authorize @issue
    @issue.destroy
    redirect_to project_issues_path(@project), notice: "Issue deleted."
  end

  private

  def set_project
    @project = Project.find(params[:project_id])
  end

  def set_issue
    @issue = @project.issues.find(params[:id])
  end

  def issue_params
    params.require(:issue).permit(:title, :description, :status, :priority,
                                  :estimate_hours, :assignee_id, :sprint_id)
  end
end
