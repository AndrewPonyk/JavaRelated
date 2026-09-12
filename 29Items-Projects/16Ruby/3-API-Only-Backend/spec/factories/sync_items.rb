# frozen_string_literal: true

FactoryBot.define do
  factory :sync_item do
    user
    collection_name { 'notes' }
    sequence(:record_id) { |n| "record-#{n}" }
    payload { { 'title' => 'Test note' } }
    client_updated_at { Time.current }
    last_synced_at { Time.current }
  end
end
