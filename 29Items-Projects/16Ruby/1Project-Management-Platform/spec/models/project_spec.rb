require "rails_helper"

RSpec.describe Project, type: :model do
  subject { build(:project) }

  it { is_expected.to validate_presence_of(:name) }
  it { is_expected.to validate_presence_of(:key) }

  it "rejects an invalid key format" do
    project = build(:project, key: "lowercase")
    expect(project).not_to be_valid
    expect(project.errors[:key]).to be_present
  end

  describe "#active_sprint" do
    it "returns the active sprint if one exists" do
      project = create(:project)
      create(:sprint, project: project, state: "planned")
      active = create(:sprint, project: project, state: "active")
      expect(project.active_sprint).to eq(active)
    end

    it "returns nil when no active sprint" do
      project = create(:project)
      expect(project.active_sprint).to be_nil
    end
  end
end
