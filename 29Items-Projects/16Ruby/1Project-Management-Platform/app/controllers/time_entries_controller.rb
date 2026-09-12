class TimeEntriesController < ApplicationController
  before_action :set_issue

  def create
    @time_entry = @issue.time_entries.build(time_entry_params.merge(user: current_user))
    authorize @time_entry

    if @time_entry.save
      respond_to do |format|
        format.html { redirect_to project_issue_path(@issue.project, @issue), notice: "Time logged." }
        format.turbo_stream
      end
    else
      redirect_to project_issue_path(@issue.project, @issue),
                  alert: @time_entry.errors.full_messages.to_sentence,
                  status: :see_other
    end
  end

  def destroy
    @time_entry = @issue.time_entries.find(params[:id])
    authorize @time_entry
    @time_entry.destroy
    redirect_to project_issue_path(@issue.project, @issue), notice: "Time entry removed."
  end

  private

  def set_issue
    scope = Issue.joins(project: :memberships).where(memberships: { user_id: current_user.id })
    @issue = scope.find(params[:issue_id])
  end

  def time_entry_params
    params.require(:time_entry).permit(:hours, :worked_on, :note)
  end
end
