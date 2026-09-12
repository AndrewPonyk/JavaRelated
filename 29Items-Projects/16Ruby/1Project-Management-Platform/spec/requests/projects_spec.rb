require "rails_helper"

RSpec.describe "Projects", type: :request do
  let(:user) { create(:user) }

  before { sign_in user }

  it "lists only projects the user belongs to" do
    mine    = create(:project, owner: user)
    _other  = create(:project)

    get projects_path
    expect(response).to have_http_status(:ok)
    expect(response.body).to include(mine.name)
  end

  it "creates a project and makes creator an admin" do
    expect {
      post projects_path, params: {
        project: { name: "Created", key: "CRT", description: "x" }
      }
    }.to change(Project, :count).by(1)
    project = Project.find_by(key: "CRT")
    expect(project.memberships.find_by(user: user)).to be_admin
  end
end
