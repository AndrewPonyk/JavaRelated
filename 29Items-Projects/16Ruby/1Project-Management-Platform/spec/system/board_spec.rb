require "rails_helper"

RSpec.describe "Issue board", type: :system do
  before do
    driven_by(:rack_test)
  end

  let(:user) { create(:user) }
  let(:project) { create(:project, owner: user) }

  it "renders columns for each status" do
    create(:issue, project: project, status: "todo", title: "Do a thing")
    create(:issue, project: project, status: "in_progress", title: "Doing another")

    sign_in user
    visit project_board_path(project)

    expect(page).to have_content("To Do")
    expect(page).to have_content("In Progress")
    expect(page).to have_content("Do a thing")
    expect(page).to have_content("Doing another")
  end
end
