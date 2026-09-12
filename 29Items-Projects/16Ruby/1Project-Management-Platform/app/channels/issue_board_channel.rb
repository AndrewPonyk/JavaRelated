class IssueBoardChannel < ApplicationCable::Channel
  def subscribed
    project = Project.find(params[:project_id])
    reject unless Pundit.policy(current_user, project).show?

    stream_for [project, :board]
  end

  def unsubscribed
    stop_all_streams
  end
end
