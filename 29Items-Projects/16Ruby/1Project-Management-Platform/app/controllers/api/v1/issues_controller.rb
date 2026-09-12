module Api
  module V1
    class IssuesController < BaseController
      before_action :set_project
      before_action :set_issue, only: %i[show update destroy]

      def index
        issues = policy_scope(@project.issues).includes(:assignee, :sprint)
                                              .page(params[:page])
        render json: { data: issues.map { |i| serialize(i) } }
      end

      def show
        authorize @issue
        render json: { data: serialize(@issue) }
      end

      def create
        issue = @project.issues.build(issue_params.merge(reporter: current_user))
        authorize issue
        issue.save!
        render json: { data: serialize(issue) }, status: :created
      end

      def update
        authorize @issue
        @issue.update!(issue_params)
        render json: { data: serialize(@issue) }
      end

      def destroy
        authorize @issue
        @issue.destroy
        head :no_content
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

      def serialize(issue)
        {
          id: issue.id,
          title: issue.title,
          status: issue.status,
          priority: issue.priority,
          estimate_hours: issue.estimate_hours,
          logged_hours: issue.logged_hours,
          assignee_id: issue.assignee_id,
          sprint_id: issue.sprint_id,
          updated_at: issue.updated_at.iso8601
        }
      end
    end
  end
end
