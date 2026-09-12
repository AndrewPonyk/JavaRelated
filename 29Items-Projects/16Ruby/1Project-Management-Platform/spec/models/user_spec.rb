require "rails_helper"

RSpec.describe User, type: :model do
  it { is_expected.to have_many(:memberships).dependent(:destroy) }
  it { is_expected.to have_many(:projects).through(:memberships) }
  it { is_expected.to validate_presence_of(:name) }

  it "issues an api_token on create" do
    user = create(:user)
    expect(user.api_token).to be_present
  end

  it "returns display_name from name when present" do
    user = build(:user, name: "Alice")
    expect(user.display_name).to eq("Alice")
  end

  it "falls back to email local-part when name is blank" do
    user = build(:user, name: "", email: "bob@example.com")
    expect(user.display_name).to eq("bob")
  end

  it "#admin? reflects admin_flag" do
    expect(build(:user, :admin)).to be_admin
    expect(build(:user)).not_to be_admin
  end
end
