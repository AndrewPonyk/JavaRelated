class WorkloadPredictionJob < ApplicationJob
  queue_as :default

  def perform(sprint_id)
    sprint = Sprint.find(sprint_id)
    result = WorkloadPredictor.new(sprint).call

    prediction = WorkloadPrediction.create!(
      sprint: sprint,
      predicted_hours: result.predicted_hours,
      confidence: result.confidence,
      model_version: result.model_version
    )

    Turbo::StreamsChannel.broadcast_replace_to(
      [sprint.project, :sprint_metrics],
      target: "sprint_#{sprint.id}_forecast",
      partial: "sprints/forecast",
      locals: { sprint: sprint, prediction: prediction }
    )
  end
end
