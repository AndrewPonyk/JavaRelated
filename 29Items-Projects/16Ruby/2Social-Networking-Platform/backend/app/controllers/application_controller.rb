class ApplicationController < ActionController::API
  before_action :set_request_id

  rescue_from ActiveRecord::RecordNotFound, with: :render_not_found
  rescue_from ActionController::ParameterMissing, with: :render_bad_request
  rescue_from ActionController::BadRequest, with: :render_bad_request
  rescue_from ActiveRecord::RecordInvalid, with: :render_unprocessable_entity
  rescue_from Auth::UnauthorizedError, with: :render_unauthorized
  rescue_from Auth::ForbiddenError, with: :render_forbidden

  private

  def current_user
    return @current_user if defined?(@current_user)

    token = request.authorization.to_s.match(/\ABearer (.+)\z/) { |match| match[1] }
    @current_user = token ? Auth::TokenService.user_from_token(token) : nil
  end

  def authenticate_user!
    current_user || raise(Auth::UnauthorizedError, "Authentication required")
  end

  def authorize_owner!(record, user: current_user)
    owner_id =
      if record.respond_to?(:user_id)
        record.user_id
      elsif record.respond_to?(:sender_id)
        record.sender_id
      else
        record.id
      end

    raise Auth::ForbiddenError, "You are not allowed to access this resource" unless owner_id == user&.id
  end

  def render_json(resource, status: :ok)
    render json: resource, status: status
  end

  def bounded_limit(default:, maximum:)
    value = params.fetch(:limit, default).to_i
    [[value, 1].max, maximum].min
  end

  def set_request_id
    response.set_header("X-Request-Id", request.request_id)
  end

  def render_not_found(error)
    render json: { error: error.message }, status: :not_found
  end

  def render_bad_request(error)
    render json: { error: error.message }, status: :bad_request
  end

  def render_unprocessable_entity(error)
    render json: { errors: error.record.errors.full_messages }, status: :unprocessable_entity
  end

  def render_unauthorized(error)
    render json: { error: error.message }, status: :unauthorized
  end

  def render_forbidden(error)
    render json: { error: error.message }, status: :forbidden
  end
end
