require "faraday"

class WorkloadPredictor
  class PredictionError < StandardError; end

  Result = Struct.new(:predicted_hours, :confidence, :model_version, keyword_init: true)

  def initialize(sprint)
    @sprint = sprint
  end

  def call
    Stoplight("ml-predictor") { perform_request }
      .with_fallback { |_e| fallback_prediction }
      .with_threshold(3)
      .with_cool_off_time(60)
      .run
  end

  private

  attr_reader :sprint

  def perform_request
    response = client.post("/predict") do |req|
      req.headers["Authorization"] = "Bearer #{ENV.fetch('ML_PREDICTOR_API_KEY')}"
      req.body = build_payload.to_json
    end

    raise PredictionError, "ML API #{response.status}" unless response.success?

    body = response.body
    Result.new(
      predicted_hours: body.fetch("predicted_hours"),
      confidence: body.fetch("confidence"),
      model_version: body.fetch("model_version")
    )
  end

  def build_payload
    {
      sprint_id: sprint.id,
      starts_on: sprint.starts_on,
      ends_on: sprint.ends_on,
      team_size: sprint.project.users.count,
      issues: sprint.issues.map do |issue|
        {
          id: issue.id,
          priority: issue.priority,
          estimate_hours: issue.estimate_hours,
          assignee_id: issue.assignee_id
        }
      end
    }
  end

  def fallback_prediction
    # Conservative heuristic when ML is unreachable
    Result.new(
      predicted_hours: sprint.total_estimate.to_f * 1.2,
      confidence: 0.4,
      model_version: "fallback-heuristic"
    )
  end

  def client
    @client ||= Faraday.new(url: ENV.fetch("ML_PREDICTOR_URL")) do |f|
      f.request :json
      f.response :json
      f.request :retry, max: 2, interval: 0.2, backoff_factor: 2
      f.options.timeout = ENV.fetch("ML_PREDICTOR_TIMEOUT", 5).to_i
    end
  end
end
