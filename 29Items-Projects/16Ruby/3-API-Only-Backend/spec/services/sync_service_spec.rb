# frozen_string_literal: true

require 'rails_helper'

RSpec.describe SyncService do
  it 'applies client changes and returns synced records' do
    user = create(:user)
    result = described_class.new(
      user:,
      changes: [
        {
          collection_name: 'notes',
          record_id: 'client-1',
          payload: { title: 'Offline note' },
          client_updated_at: Time.current.iso8601
        }
      ]
    ).call

    expect(result.status).to eq('success')
    expect(result.applied.size).to eq(1)
    expect(user.sync_items.find_by(record_id: 'client-1').payload).to eq('title' => 'Offline note')
  end

  it 'reports conflicts when the server has a newer version' do
    user = create(:user)
    create(:sync_item, user:, record_id: 'client-1', client_updated_at: 1.hour.from_now, payload: { title: 'Server' })

    result = described_class.new(
      user:,
      changes: [
        {
          collection_name: 'notes',
          record_id: 'client-1',
          payload: { title: 'Client' },
          client_updated_at: Time.current.iso8601
        }
      ]
    ).call

    expect(result.status).to eq('conflict')
    expect(result.conflicts.first.fetch(:server_item).fetch('payload')).to eq('title' => 'Server')
  end
end
