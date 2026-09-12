FactoryBot.define do
  factory :sprint do
    project
    sequence(:name) { |n| "Sprint #{n}" }
    starts_on { Date.current }
    ends_on   { Date.current + 14.days }
    state     { "planned" }
    goal      { "Deliver value" }
  end
end
