FactoryBot.define do
  factory :notification do
    user
    kind { "message" }
    payload { { "message_id" => 1 } }
  end
end
