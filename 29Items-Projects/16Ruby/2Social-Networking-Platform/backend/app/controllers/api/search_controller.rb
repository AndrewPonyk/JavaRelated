module Api
  class SearchController < ApplicationController
    def index
      raise ActionController::ParameterMissing, :q unless params.key?(:q)

      query = params[:q].to_s.strip
      raise ActionController::BadRequest, "Search query cannot be blank" if query.blank?

      posts = SearchIndexer.new.search_posts(query, viewer: current_user, limit: bounded_limit(default: 20, maximum: 50))
      render json: posts.map { |post| ResourceSerializer.post(post) }
    end
  end
end
