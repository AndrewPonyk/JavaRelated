class HealthController < ActionController::Base
  def show
    ActiveRecord::Base.connection.execute("SELECT 1")
    redis_ok = Sidekiq.redis(&:ping) == "PONG"
    render json: { status: "ok", db: "ok", redis: redis_ok ? "ok" : "down" }
  rescue StandardError => e
    render json: { status: "error", error: e.message }, status: :service_unavailable
  end
end
