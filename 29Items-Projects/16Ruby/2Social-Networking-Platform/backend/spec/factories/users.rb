FactoryBot.define do
  factory :user do
    sequence(:email) { |number| "user#{number}@example.com" }
    sequence(:username) { |number| "user_#{number}" }
    display_name { "Test User" }
    password { "password123" }
    password_confirmation { "password123" }
  end
end
