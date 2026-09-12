# frozen_string_literal: true

class ApplicationController < ActionController::API
  class UnauthorizedError < StandardError; end
  class ForbiddenError < StandardError; end

  DEFAULT_PER_PAGE = 25
  MAX_PER_PAGE = 100

  before_action :mark_request_start
  before_action :authenticate_user!
  after_action :track_request_metric

  rescue_from StandardError do |error|
    Rails.logger.error("unhandled_error=#{error.class.name} message=#{error.message}")
    render_error('internal_server_error', 'An unexpected error occurred', :internal_server_error)
  end

  rescue_from ActiveRecord::RecordNotFound do |error|
    render_error('not_found', error.message, :not_found)
  end

  rescue_from ActiveRecord::RecordInvalid do |error|
    render_error('validation_error', error.record.errors.full_messages, :unprocessable_content)
  end

  rescue_from ActionController::ParameterMissing do |error|
    render_error('bad_request', error.message, :bad_request)
  end

  rescue_from UnauthorizedError do |error|
    render_error('unauthorized', error.message, :unauthorized)
  end

  rescue_from ForbiddenError do |error|
    render_error('forbidden', error.message, :forbidden)
  end

  attr_reader :current_token_expires_at, :current_token_jti, :current_user

  private

  def authenticate_user!
    token = bearer_token
    raise UnauthorizedError, 'Missing bearer token' if token.blank?

    payload = AuthTokenService.decode(token)
    raise UnauthorizedError, 'Token has been revoked' if AuthTokenService.revoked?(payload.fetch('jti'))

    @current_token_jti = payload.fetch('jti')
    @current_token_expires_at = Time.zone.at(payload.fetch('exp'))
    @current_user = User.find(payload.fetch('sub'))
  rescue AuthTokenService::TokenError => e
    raise UnauthorizedError, e.message
  end

  def mark_request_start
    @request_started_at = Process.clock_gettime(Process::CLOCK_MONOTONIC)
  end

  def authorize_user!(user)
    return if current_user.id == user.id

    raise ForbiddenError, 'You can only access your own user record'
  end

  def bearer_token
    request.authorization.to_s[/\ABearer (.+)\z/, 1]
  end

  def render_error(code, message, status)
    render json: { error: { code:, message: } }, status:
  end

  def paginated(scope)
    page = bounded_integer(params[:page], default: 1, minimum: 1, maximum: 10_000)
    per_page = bounded_integer(params[:per_page], default: DEFAULT_PER_PAGE, minimum: 1, maximum: MAX_PER_PAGE)
    total_count = scope.count

    [
      scope.limit(per_page).offset((page - 1) * per_page),
      {
        page:,
        per_page:,
        total_count:,
        total_pages: (total_count.to_f / per_page).ceil
      }
    ]
  end

  def bounded_integer(value, default:, minimum:, maximum:)
    parsed = Integer(value.presence || default)
    parsed.clamp(minimum, maximum)
  rescue ArgumentError, TypeError
    default
  end

  def track_request_metric
    MetricsService.track_request(
      request:,
      response:,
      user: current_user,
      started_at: @request_started_at
    )
  rescue StandardError => e
    Rails.logger.warn("metric_tracking_failed=#{e.class.name} message=#{e.message}")
  end
end
