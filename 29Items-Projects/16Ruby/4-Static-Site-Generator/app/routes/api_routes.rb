# frozen_string_literal: true

require "json"
require_relative "../configuration"
require_relative "../errors"
require_relative "../repositories/page_repository"
require_relative "../services/site_builder"
require_relative "../services/version_index"

module ApiRoutes
  MAX_JSON_BODY_BYTES = 128 * 1024
  MAX_PAGE_LIMIT = 100
  MAX_SEARCH_QUERY_LENGTH = 80

  def self.registered(app)
    app.helpers do
      def parse_json_body(request)
        raw = request.body.read
        raise BadRequestError, "Request body is required" if raw.to_s.strip.empty?
        raise BadRequestError, "Request body is too large" if raw.bytesize > MAX_JSON_BODY_BYTES

        payload = JSON.parse(raw)
        raise BadRequestError, "JSON body must be an object" unless payload.is_a?(Hash)

        payload
      rescue JSON::ParserError
        raise BadRequestError, "Request body must be valid JSON"
      end

      def page_repository
        PageRepository.new
      end

      def site_builder
        SiteBuilder.new
      end

      def rebuild_preview_index
        site_builder.build!
      end

      def page_limit
        limit = params.fetch("limit", "50").to_i
        [[limit, 1].max, MAX_PAGE_LIMIT].min
      end

      def page_offset
        [params.fetch("offset", "0").to_i, 0].max
      end

      def require_json_content_type!
        content_type = request.media_type.to_s
        raise BadRequestError, "Content-Type must be application/json" unless content_type == "application/json"
      end

      def require_write_token!
        token = ENV["API_AUTH_TOKEN"].to_s
        return if token.empty? && Configuration.environment != "production"

        if token.empty?
          raise AppError.new(
            "API write token is not configured",
            status: 503,
            code: "service_unavailable"
          )
        end

        provided = request.env["HTTP_AUTHORIZATION"].to_s.sub(/\ABearer\s+/i, "")
        return if provided.bytesize == token.bytesize && Rack::Utils.secure_compare(provided, token)

        raise AppError.new("Unauthorized", status: 401, code: "unauthorized")
      end
    end

    app.before "/api/*" do
      content_type :json

      if request.post? && request.path_info == "/api/pages"
        require_json_content_type!
        require_write_token!
      elsif request.path_info.start_with?("/api/pages/") && (request.put? || request.delete?)
        require_json_content_type! if request.put?
        require_write_token!
      elsif request.post? && request.path_info == "/api/build"
        require_write_token!
      end
    end

    app.get "/api/pages" do
      pages = page_repository.all(sync_metadata: false)
      paged = pages.drop(page_offset).first(page_limit)
      {
        total: pages.size,
        limit: page_limit,
        offset: page_offset,
        pages: paged.map(&:to_h)
      }.to_json
    end

    app.get "/api/pages/:version/:slug" do
      page = page_repository.find(version: params[:version], slug: params[:slug], sync_metadata: false)
      page.to_h.merge(html: page.html).to_json
    end

    app.post "/api/pages" do
      payload = parse_json_body(request)
      page = page_repository.create(payload)
      rebuild_preview_index
      status 201
      page.to_h.merge(html: page.html).to_json
    end

    app.put "/api/pages/:version/:slug" do
      payload = parse_json_body(request)
      page = page_repository.update(version: params[:version], slug: params[:slug], attributes: payload)
      rebuild_preview_index
      page.to_h.merge(html: page.html).to_json
    end

    app.delete "/api/pages/:version/:slug" do
      page = page_repository.delete(version: params[:version], slug: params[:slug])
      rebuild_preview_index
      { status: "deleted", page: page.to_h }.to_json
    end

    app.get "/api/search" do
      query = params.fetch("q", "").to_s.strip
      raise BadRequestError, "Search query is too long" if query.length > MAX_SEARCH_QUERY_LENGTH

      limit = [[params.fetch("limit", "10").to_i, 1].max, 50].min
      results = site_builder.search(query, limit: limit)
      { query: query, limit: limit, results: results }.to_json
    end

    app.get "/api/versions" do
      VersionIndex.new.to_h.to_json
    end

    app.post "/api/build" do
      pages = site_builder.build!
      { status: "built", pages: pages.size }.to_json
    end
  end
end
