require "rails_helper"

RSpec.describe "API V1 Issues", type: :request do
  let(:user)    { create(:user) }
  let(:project) { create(:project, owner: user) }
  let(:headers) { { "Authorization" => "Bearer #{user.api_token}", "Accept" => "application/json" } }

  describe "GET /api/v1/projects/:project_id/issues" do
    it "returns project issues as JSON" do
      issue = create(:issue, project: project)
      get "/api/v1/projects/#{project.id}/issues", headers: headers
      expect(response).to have_http_status(:ok)
      json = response.parsed_body
      expect(json["data"].map { |i| i["id"] }).to include(issue.id)
    end

    it "rejects missing auth" do
      get "/api/v1/projects/#{project.id}/issues"
      expect(response).to have_http_status(:unauthorized)
    end
  end

  describe "POST /api/v1/projects/:project_id/issues" do
    it "creates an issue" do
      post "/api/v1/projects/#{project.id}/issues",
           params: { issue: { title: "API issue", status: "todo", priority: "medium" } }.to_json,
           headers: headers.merge("Content-Type" => "application/json")
      expect(response).to have_http_status(:created)
      expect(response.parsed_body.dig("data", "title")).to eq("API issue")
    end

    it "returns validation errors" do
      post "/api/v1/projects/#{project.id}/issues",
           params: { issue: { title: "", priority: "medium", status: "todo" } }.to_json,
           headers: headers.merge("Content-Type" => "application/json")
      expect(response).to have_http_status(:unprocessable_entity)
      expect(response.parsed_body["error"]).to eq("validation_failed")
    end
  end

  describe "DELETE /api/v1/projects/:project_id/issues/:id" do
    it "deletes an issue when reporter" do
      issue = create(:issue, project: project, reporter: user)
      delete "/api/v1/projects/#{project.id}/issues/#{issue.id}", headers: headers
      expect(response).to have_http_status(:no_content)
    end
  end
end
