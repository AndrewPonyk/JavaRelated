class IssueStateMachine
  TRANSITIONS = {
    "backlog"     => %w[todo in_progress],
    "todo"        => %w[backlog in_progress],
    "in_progress" => %w[todo review done],
    "review"      => %w[in_progress done],
    "done"        => %w[in_progress]
  }.freeze

  def initialize(issue)
    @issue = issue
  end

  def can_transition?(new_status)
    TRANSITIONS.fetch(@issue.status, []).include?(new_status)
  end

  def transition!(new_status)
    raise ArgumentError, "Invalid transition #{@issue.status} -> #{new_status}" unless can_transition?(new_status)

    @issue.update!(status: new_status)
  end
end
