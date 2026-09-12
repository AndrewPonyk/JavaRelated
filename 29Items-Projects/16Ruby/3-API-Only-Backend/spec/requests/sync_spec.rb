# frozen_string_literal: true

require 'rails_helper'

RSpec.describe 'Sync API', type: :request do
  it 'syncs client changes and reports status' do
    user = create(:user)

    post '/api/v1/sync',
         params: {
           changes: [
             {
               collection_name: 'notes',
               record_id: 'mobile-1',
               payload: { title: 'Mobile note' },
               client_updated_at: Time.current.iso8601
             }
           ]
         }.to_json,
         headers: auth_headers(user)

    expect(response).to have_http_status(:ok)
    expect(json.fetch('applied').size).to eq(1)

    get '/api/v1/sync/status', headers: auth_headers(user)

    expect(response).to have_http_status(:ok)
    expect(json.fetch('status')).to eq('success')
    expect(json.fetch('totalRecords')).to eq(1)
  end

  it 'supports CRUD for sync items' do
    user = create(:user)

    post '/api/v1/sync_items',
         params: {
           sync_item: {
             collection_name: 'tasks',
             record_id: 'task-1',
             payload: { title: 'Task' },
             client_updated_at: Time.current.iso8601
           }
         }.to_json,
         headers: auth_headers(user)

    expect(response).to have_http_status(:created)
    item_id = json.dig('sync_item', 'id')

    patch "/api/v1/sync_items/#{item_id}",
          params: { sync_item: { payload: { title: 'Updated task' } } }.to_json,
          headers: auth_headers(user)

    expect(response).to have_http_status(:ok)
    expect(json.dig('sync_item', 'payload', 'title')).to eq('Updated task')

    delete "/api/v1/sync_items/#{item_id}", headers: auth_headers(user)

    expect(response).to have_http_status(:ok)
    expect(json.dig('sync_item', 'deleted?')).to be(true)
  end

  it 'paginates sync item lists' do
    user = create(:user)
    create_list(:sync_item, 3, user:)

    get '/api/v1/sync_items?page=1&per_page=2', headers: auth_headers(user)

    expect(response).to have_http_status(:ok)
    expect(json.fetch('sync_items').size).to eq(2)
    expect(json.dig('meta', 'total_count')).to eq(3)
    expect(json.dig('meta', 'total_pages')).to eq(2)
  end

  it 'returns validation errors for malformed sync changes' do
    user = create(:user)

    post '/api/v1/sync',
         params: { changes: [{ collection_name: 'notes', record_id: 'bad', payload: 'not-object' }] }.to_json,
         headers: auth_headers(user)

    expect(response).to have_http_status(:unprocessable_content)
    expect(json.dig('error', 'code')).to eq('invalid_sync_change')
  end
end
