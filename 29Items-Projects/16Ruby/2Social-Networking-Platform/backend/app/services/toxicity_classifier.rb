class ToxicityClassifier
  DEFAULT_MODEL_VERSION = "local-rules-v1".freeze

  def initialize(endpoint: ENV.fetch("TOXICITY_CLASSIFIER_URL", nil))
    @endpoint = endpoint
  end

  def classify(text:)
    remote_result = classify_remotely(text)
    return remote_result if remote_result

    normalized = text.downcase
    toxic_terms = %w[spam abuse hate threat harass]
    matches = toxic_terms.count { |term| normalized.include?(term) }
    score = [matches * 0.35, 0.98].min

    {
      score: score,
      label: score >= 0.7 ? "toxic" : "safe",
      model_version: DEFAULT_MODEL_VERSION
    }
  end

  private

  attr_reader :endpoint

  def classify_remotely(text)
    return nil if endpoint.blank?

    uri = URI(endpoint)
    request = Net::HTTP::Post.new(uri)
    request["Content-Type"] = "application/json"
    request.body = { text: text }.to_json

    response = Net::HTTP.start(uri.hostname, uri.port, use_ssl: uri.scheme == "https", read_timeout: 2, open_timeout: 1) do |http|
      http.request(request)
    end
    return nil unless response.is_a?(Net::HTTPSuccess)

    parsed = JSON.parse(response.body)
    {
      score: Float(parsed.fetch("score")),
      label: parsed.fetch("label"),
      model_version: parsed.fetch("model_version")
    }
  rescue JSON::ParserError, KeyError, ArgumentError, SocketError, Errno::ECONNREFUSED, Net::OpenTimeout, Net::ReadTimeout
    nil
  end
end
