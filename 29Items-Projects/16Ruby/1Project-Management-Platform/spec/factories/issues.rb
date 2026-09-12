FactoryBot.define do
  factory :issue do
    project
    reporter { project.users.first || create(:user).tap { |u| create(:membership, user: u, project: project) } }
    title    { Faker::Lorem.sentence(word_count: 4) }
    description { Faker::Lorem.paragraph }
    status   { "todo" }
    priority { "medium" }
    estimate_hours { 4 }
  end
end
