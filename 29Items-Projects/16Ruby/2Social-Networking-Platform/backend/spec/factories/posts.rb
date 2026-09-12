FactoryBot.define do
  factory :post do
    user
    body { "A useful social post" }
    visibility { "public" }
  end
end
