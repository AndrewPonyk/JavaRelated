class SprintsController < ApplicationController
  before_action :set_project
  before_action :set_sprint, only: %i[show edit update destroy]

  def index
    @sprints = policy_scope(@project.sprints).order(starts_on: :desc)
  end

  def show
    authorize @sprint
    @issues = @sprint.issues.includes(:assignee)
    @latest_prediction = @sprint.workload_predictions.order(created_at: :desc).first
  end

  def new
    @sprint = @project.sprints.build
    authorize @sprint
  end

  def create
    @sprint = @project.sprints.build(sprint_params)
    authorize @sprint
    if @sprint.save
      WorkloadPredictionJob.perform_later(@sprint.id)
      redirect_to [@project, @sprint], notice: "Sprint created."
    else
      render :new, status: :unprocessable_entity
    end
  end

  def edit
    authorize @sprint
  end

  def update
    authorize @sprint
    if @sprint.update(sprint_params)
      WorkloadPredictionJob.perform_later(@sprint.id) if @sprint.saved_change_to_ends_on?
      redirect_to [@project, @sprint], notice: "Sprint updated."
    else
      render :edit, status: :unprocessable_entity
    end
  end

  def destroy
    authorize @sprint
    @sprint.destroy
    redirect_to project_sprints_path(@project), notice: "Sprint deleted."
  end

  private

  def set_project
    @project = Project.find(params[:project_id])
  end

  def set_sprint
    @sprint = @project.sprints.find(params[:id])
  end

  def sprint_params
    params.require(:sprint).permit(:name, :starts_on, :ends_on, :state, :goal)
  end
end
