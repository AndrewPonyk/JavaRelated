module Ml
  # Thin HTTP wrapper for the external workload prediction service.
  # WorkloadPredictor wraps this with circuit breaker + fallback.
  class WorkloadModelClient
    Error = Class.new(StandardError)

    def initialize(url: ENV.fetch("ML_PREDICTOR_URL"),
                   api_key: ENV.fetch("ML_PREDICTOR_API_KEY"),
                   timeout: ENV.fetch("ML_PREDICTOR_TIMEOUT", 5).to_i)
      @url = url
      @api_key = api_key
      @timeout = timeout
    end

    def predict(payload)
      response = connection.post("/predict") do |req|
        req.headers["Authorization"] = "Bearer #{@api_key}"
        req.body = payload.to_json
      end
      raise Error, "Status #{response.status}" unless response.success?

      response.body
    end

    private

    def connection
      @connection ||= Faraday.new(url: @url) do |f|
        f.request :json
        f.response :json
        f.request :retry, max: 2, interval: 0.2, backoff_factor: 2
        f.options.timeout = @timeout
      end
    end
  end
end
