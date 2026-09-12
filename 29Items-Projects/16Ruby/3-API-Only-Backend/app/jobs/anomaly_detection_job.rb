# frozen_string_literal: true

class AnomalyDetectionJob < ApplicationJob
  queue_as :default

  def perform
    AnomalyDetectionService.new.call
  end
end
