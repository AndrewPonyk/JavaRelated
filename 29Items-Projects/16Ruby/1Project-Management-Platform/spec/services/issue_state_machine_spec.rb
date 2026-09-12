require "rails_helper"

RSpec.describe IssueStateMachine do
  let(:issue) { create(:issue, status: "todo") }

  it "allows todo → in_progress" do
    expect(described_class.new(issue).can_transition?("in_progress")).to be true
  end

  it "rejects todo → done" do
    expect(described_class.new(issue).can_transition?("done")).to be false
  end

  it "raises on invalid transition" do
    expect { described_class.new(issue).transition!("done") }.to raise_error(ArgumentError)
  end

  it "persists valid transition" do
    described_class.new(issue).transition!("in_progress")
    expect(issue.reload.status).to eq("in_progress")
  end
end
