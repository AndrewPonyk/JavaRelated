class SearchIndexer
  INDEX_NAME = "social_posts".freeze

  def initialize(endpoint: Rails.application.config.x.elasticsearch_url)
    @endpoint = endpoint
  end

  def index_post(post)
    document = {
      id: post.id,
      body: post.body,
      user_id: post.user_id,
      username: post.user.username,
      visibility: post.visibility,
      created_at: post.created_at.iso8601
    }

    put_json("/#{INDEX_NAME}/_doc/#{post.id}", document)
  end

  def delete_post(post_id)
    request(:delete, "/#{INDEX_NAME}/_doc/#{post_id}")
  rescue StandardError => e
    Rails.logger.warn(event: "search.delete_failed", post_id: post_id, error: e.message)
    {}
  end

  def search_posts(query, viewer:, limit: 20)
    normalized_query = query.to_s.strip
    return Post.none if normalized_query.blank?

    response = post_json(
      "/#{INDEX_NAME}/_search",
      {
        size: limit,
        query: {
          multi_match: {
            query: normalized_query,
            fields: %w[body username]
          }
        }
      }
    )

    ids = response.dig("hits", "hits").to_a.map { |hit| hit.dig("_source", "id") }
    return fallback_search(normalized_query, viewer: viewer, limit: limit) if ids.empty?

    Post.visible_to(viewer).includes(:user, :toxicity_result).where(id: ids).order(created_at: :desc)
  rescue StandardError => e
    Rails.logger.warn(event: "search.fallback", error: e.class.name, message: e.message)
    fallback_search(normalized_query, viewer: viewer, limit: limit)
  end

  private

  attr_reader :endpoint

  def fallback_search(query, viewer:, limit:)
    Post.visible_to(viewer)
        .includes(:user, :toxicity_result)
        .where("body ILIKE ?", "%#{ActiveRecord::Base.sanitize_sql_like(query)}%")
        .order(created_at: :desc)
        .limit(limit)
  end

  def put_json(path, body)
    request(:put, path, body)
  end

  def post_json(path, body)
    request(:post, path, body)
  end

  def request(method, path, body = nil)
    uri = URI.join(endpoint, path)
    klass = { put: Net::HTTP::Put, post: Net::HTTP::Post, delete: Net::HTTP::Delete }.fetch(method)
    request = klass.new(uri)
    request["Content-Type"] = "application/json"
    request.body = body.to_json if body

    response = Net::HTTP.start(uri.hostname, uri.port, use_ssl: uri.scheme == "https", read_timeout: 2, open_timeout: 1) do |http|
      http.request(request)
    end
    response.body.present? ? JSON.parse(response.body) : {}
  end
end
