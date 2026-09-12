FactoryBot.define do
  factory :follow do
    association :follower, factory: :user
    association :followee, factory: :user
    notifications_enabled { true }
  end
end
