FactoryBot.define do
  factory :toxicity_result do
    post
    score { 0.02 }
    label { "safe" }
    model_version { "test-v1" }
  end
end
