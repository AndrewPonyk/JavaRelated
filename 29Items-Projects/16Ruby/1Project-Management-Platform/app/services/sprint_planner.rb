class SprintPlanner
  # TODO: implement capacity-aware sprint planning:
  #   - compute team capacity (users × working hours − time off)
  #   - weight by ML prediction confidence
  #   - suggest issue subset that maximizes priority within capacity (knapsack-ish)

  def initialize(project, capacity_hours:)
    @project = project
    @capacity_hours = capacity_hours
  end

  def suggest(candidate_issues)
    # Greedy priority-weighted fit. Replace with ILP solver if needed.
    sorted = candidate_issues.sort_by { |i| -priority_weight(i) }
    remaining = @capacity_hours
    sorted.each_with_object([]) do |issue, picked|
      next if issue.estimate_hours.to_f > remaining

      picked << issue
      remaining -= issue.estimate_hours.to_f
    end
  end

  private

  def priority_weight(issue)
    { "urgent" => 4, "high" => 3, "medium" => 2, "low" => 1 }.fetch(issue.priority, 0)
  end
end
