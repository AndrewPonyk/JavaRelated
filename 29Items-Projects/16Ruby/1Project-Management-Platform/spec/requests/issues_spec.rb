require "rails_helper"

RSpec.describe "Issues", type: :request do
  let(:user)    { create(:user) }
  let(:project) { create(:project, owner: user) }

  before { sign_in user }

  describe "GET /projects/:project_id/issues" do
    it "renders the issues list" do
      issue = create(:issue, project: project)
      get project_issues_path(project)
      expect(response).to have_http_status(:ok)
      expect(response.body).to include(issue.title)
    end
  end

  describe "POST /projects/:project_id/issues" do
    it "creates an issue" do
      expect {
        post project_issues_path(project), params: {
          issue: { title: "New bug", status: "todo", priority: "high", estimate_hours: 3 }
        }
      }.to change(Issue, :count).by(1)
      expect(response).to redirect_to(project_issue_path(project, Issue.last))
    end

    it "re-renders form on validation failure" do
      post project_issues_path(project), params: { issue: { title: "", status: "todo", priority: "high" } }
      expect(response).to have_http_status(:unprocessable_entity)
    end
  end

  describe "PATCH /projects/:project_id/issues/:id" do
    it "updates the status and broadcasts" do
      issue = create(:issue, project: project, status: "todo")
      patch project_issue_path(project, issue), params: { issue: { status: "in_progress" } }
      expect(issue.reload.status).to eq("in_progress")
    end
  end
end
