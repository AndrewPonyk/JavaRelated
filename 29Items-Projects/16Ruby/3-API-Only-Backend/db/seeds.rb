demo_password = ENV.fetch('DEMO_PASSWORD') do
  raise 'Set DEMO_PASSWORD before running db:seed'
end

demo = User.find_or_initialize_by(email: 'demo@example.com')
demo.password = demo_password
demo.password_confirmation = demo_password
demo.save!

demo.sync_items.find_or_create_by!(collection_name: 'notes', record_id: 'welcome') do |item|
  item.payload = { title: 'Welcome', body: 'This record was created by db:seed.' }
  item.client_updated_at = Time.current
  item.last_synced_at = Time.current
end
