FactoryBot.define do
  factory :time_entry do
    issue
    user { issue.project.users.first || create(:user).tap { |u| create(:membership, user: u, project: issue.project) } }
    hours { 1.5 }
    worked_on { Date.current }
    note { "Work log" }
  end

  factory :workload_prediction do
    sprint
    predicted_hours { 40.0 }
    confidence { 0.75 }
    model_version { "v1" }
  end
end
