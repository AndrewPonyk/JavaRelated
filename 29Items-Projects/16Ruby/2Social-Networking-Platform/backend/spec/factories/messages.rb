FactoryBot.define do
  factory :message do
    association :sender, factory: :user
    association :recipient, factory: :user
    body { "Hello from the test suite" }
  end
end
