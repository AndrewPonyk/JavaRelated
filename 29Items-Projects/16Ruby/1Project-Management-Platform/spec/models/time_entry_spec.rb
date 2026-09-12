require "rails_helper"

RSpec.describe TimeEntry, type: :model do
  it { is_expected.to belong_to(:issue) }
  it { is_expected.to belong_to(:user) }

  it "rejects non-positive hours" do
    entry = build(:time_entry, hours: 0)
    expect(entry).not_to be_valid
  end

  it "rejects hours over 24" do
    entry = build(:time_entry, hours: 25)
    expect(entry).not_to be_valid
  end

  it "requires worked_on" do
    entry = build(:time_entry, worked_on: nil)
    expect(entry).not_to be_valid
  end
end
