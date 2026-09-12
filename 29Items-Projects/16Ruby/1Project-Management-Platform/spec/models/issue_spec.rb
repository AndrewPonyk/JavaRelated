require "rails_helper"

RSpec.describe Issue, type: :model do
  describe "validations" do
    subject { build(:issue) }

    it { is_expected.to validate_presence_of(:title) }
    it { is_expected.to validate_inclusion_of(:status).in_array(Issue::STATUSES) }
    it { is_expected.to validate_inclusion_of(:priority).in_array(Issue::PRIORITIES) }
  end

  describe "associations" do
    it { is_expected.to belong_to(:project) }
    it { is_expected.to belong_to(:sprint).optional }
    it { is_expected.to belong_to(:reporter).class_name("User") }
    it { is_expected.to belong_to(:assignee).class_name("User").optional }
    it { is_expected.to have_many(:time_entries).dependent(:destroy) }
  end

  describe "#logged_hours" do
    it "sums hours across time entries" do
      issue = create(:issue)
      create(:time_entry, issue: issue, hours: 2.5)
      create(:time_entry, issue: issue, hours: 1.0)

      expect(issue.logged_hours).to eq(3.5)
    end
  end

  describe "broadcasting" do
    it "broadcasts a replace when status changes" do
      issue = create(:issue, status: "todo")

      expect { issue.update!(status: "in_progress") }
        .to have_broadcasted_to([issue.project, :board]).from_channel(Turbo::StreamsChannel)
    end
  end
end
