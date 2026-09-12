FactoryBot.define do
  factory :project do
    sequence(:name) { |n| "Project #{n}" }
    sequence(:key) do |n|
      letters = ("A".."Z").to_a
      (0..2).map { |i| letters[(n + i) % 26] }.join
    end
    description { "Test project" }

    transient { owner { nil } }

    after(:create) do |project, evaluator|
      owner = evaluator.owner || create(:user)
      create(:membership, user: owner, project: project, role: "admin")
    end
  end

  factory :membership do
    user
    project
    role { "member" }
  end
end
