# frozen_string_literal: true

require 'rails_helper'

RSpec.describe SyncItem, type: :model do
  it 'enforces one record per user collection and record id' do
    existing = create(:sync_item, collection_name: 'notes', record_id: 'abc')
    duplicate = build(:sync_item, user: existing.user, collection_name: 'notes', record_id: 'abc')

    expect(duplicate).not_to be_valid
    expect(duplicate.errors[:record_id]).to be_present
  end

  it 'allows the same client record id in a different collection' do
    existing = create(:sync_item, collection_name: 'notes', record_id: 'abc')
    item = build(:sync_item, user: existing.user, collection_name: 'tasks', record_id: 'abc')

    expect(item).to be_valid
  end
end
