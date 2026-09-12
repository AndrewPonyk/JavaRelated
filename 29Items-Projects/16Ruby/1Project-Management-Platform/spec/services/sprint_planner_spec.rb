require "rails_helper"

RSpec.describe SprintPlanner do
  let(:project) { create(:project) }

  it "fits issues by priority within capacity" do
    urgent = build(:issue, project: project, priority: "urgent", estimate_hours: 5)
    low    = build(:issue, project: project, priority: "low",    estimate_hours: 3)
    high   = build(:issue, project: project, priority: "high",   estimate_hours: 4)
    overflow = build(:issue, project: project, priority: "medium", estimate_hours: 100)

    planner = described_class.new(project, capacity_hours: 10)
    picked = planner.suggest([low, urgent, high, overflow])

    expect(picked).to contain_exactly(urgent, high)
  end
end
