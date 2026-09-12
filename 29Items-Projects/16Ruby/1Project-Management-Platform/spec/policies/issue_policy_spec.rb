require "rails_helper"

RSpec.describe IssuePolicy do
  subject { described_class.new(user, issue) }

  let(:project) { create(:project) }
  let(:issue)   { create(:issue, project: project) }

  context "as a project member" do
    let(:user) { create(:user).tap { |u| create(:membership, user: u, project: project) } }

    it { is_expected.to permit_actions(%i[index? show? create? update?]) }
    it { is_expected.not_to permit_action(:destroy?) }
  end

  context "as a reporter" do
    let(:user) { issue.reporter }

    it { is_expected.to permit_action(:destroy?) }
  end

  context "as a non-member" do
    let(:user) { create(:user) }

    it { is_expected.not_to permit_actions(%i[show? update? destroy?]) }
  end
end
