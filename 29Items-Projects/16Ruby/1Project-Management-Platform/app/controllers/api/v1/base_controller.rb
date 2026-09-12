module Api
  module V1
    class BaseController < ActionController::API
      include Pundit::Authorization

      before_action :authenticate_api_user!

      rescue_from ActiveRecord::RecordNotFound do |e|
        render json: { error: "not_found", message: e.message }, status: :not_found
      end

      rescue_from ActiveRecord::RecordInvalid do |e|
        render json: { error: "validation_failed", details: e.record.errors.as_json },
               status: :unprocessable_entity
      end

      rescue_from Pundit::NotAuthorizedError do
        render json: { error: "forbidden" }, status: :forbidden
      end

      attr_reader :current_user

      private

      def authenticate_api_user!
        token = request.headers["Authorization"].to_s.sub(/\ABearer /, "").presence
        @current_user = token && User.find_by(api_token: token)
        return if @current_user

        render json: { error: "unauthorized" }, status: :unauthorized
      end
    end
  end
end
