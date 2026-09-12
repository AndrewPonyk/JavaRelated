class ProjectsController < ApplicationController
  before_action :set_project, only: %i[show edit update destroy]

  def index
    @projects = policy_scope(Project).order(:name)
  end

  def show
    authorize @project
    @active_sprint = @project.active_sprint
    @recent_issues = @project.issues.includes(:assignee).order(updated_at: :desc).limit(10)
  end

  def new
    @project = Project.new
    authorize @project
  end

  def create
    @project = Project.new(project_params)
    authorize @project

    ActiveRecord::Base.transaction do
      @project.save!
      @project.memberships.create!(user: current_user, role: "admin")
    end
    redirect_to @project, notice: "Project created."
  rescue ActiveRecord::RecordInvalid
    render :new, status: :unprocessable_entity
  end

  def edit
    authorize @project
  end

  def update
    authorize @project
    if @project.update(project_params)
      redirect_to @project, notice: "Project updated."
    else
      render :edit, status: :unprocessable_entity
    end
  end

  def destroy
    authorize @project
    @project.destroy
    redirect_to projects_path, notice: "Project deleted."
  end

  private

  def set_project
    @project = Project.find(params[:id])
  end

  def project_params
    params.require(:project).permit(:name, :key, :description)
  end
end
