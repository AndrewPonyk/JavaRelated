require "rails_helper"

RSpec.describe Sprint, type: :model do
  it { is_expected.to belong_to(:project) }
  it { is_expected.to have_many(:issues).dependent(:nullify) }

  it "requires ends_on to be after starts_on" do
    sprint = build(:sprint, starts_on: Date.current, ends_on: Date.current)
    expect(sprint).not_to be_valid
    expect(sprint.errors[:ends_on]).to be_present
  end

  describe "#total_estimate" do
    it "sums estimate_hours across issues" do
      sprint = create(:sprint)
      create(:issue, sprint: sprint, project: sprint.project, estimate_hours: 3)
      create(:issue, sprint: sprint, project: sprint.project, estimate_hours: 5)
      expect(sprint.total_estimate).to eq(8)
    end
  end

  describe "#completion_ratio" do
    it "returns 0 when no issues" do
      expect(create(:sprint).completion_ratio).to eq(0)
    end

    it "returns the fraction of done issues" do
      sprint = create(:sprint)
      create(:issue, sprint: sprint, project: sprint.project, status: "done")
      create(:issue, sprint: sprint, project: sprint.project, status: "todo")
      expect(sprint.completion_ratio).to eq(0.5)
    end
  end
end
