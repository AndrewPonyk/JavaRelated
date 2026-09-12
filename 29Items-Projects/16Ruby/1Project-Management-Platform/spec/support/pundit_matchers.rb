RSpec::Matchers.define :permit_action do |action|
  match do |policy|
    method = action.to_s.end_with?("?") ? action.to_s : "#{action}?"
    policy.public_send(method)
  end

  failure_message { |policy| "Expected #{policy.class} to permit #{action}" }
end

RSpec::Matchers.define :permit_actions do |actions|
  match do |policy|
    Array(actions).all? do |a|
      method = a.to_s.end_with?("?") ? a.to_s : "#{a}?"
      policy.public_send(method)
    end
  end
end
