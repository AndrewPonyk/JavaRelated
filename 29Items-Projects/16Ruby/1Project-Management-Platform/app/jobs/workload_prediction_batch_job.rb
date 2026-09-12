class WorkloadPredictionBatchJob < ApplicationJob
  queue_as :low

  def perform
    Sprint.active.find_each do |sprint|
      WorkloadPredictionJob.perform_later(sprint.id)
    end
  end
end
