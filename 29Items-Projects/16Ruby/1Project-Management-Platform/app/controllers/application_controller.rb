class ApplicationController < ActionController::Base
  include Pundit::Authorization

  before_action :authenticate_user!
  before_action :set_sentry_context
  after_action :verify_pundit_authorization, unless: :skip_pundit?

  rescue_from Pundit::NotAuthorizedError, with: :user_not_authorized
  rescue_from ActiveRecord::RecordNotFound, with: :record_not_found

  private

  def verify_pundit_authorization
    if action_name == "index"
      verify_policy_scoped
    else
      verify_authorized
    end
  end

  def skip_pundit?
    devise_controller? || params[:controller].match?(%r{\A(rails/|sidekiq/|health|users/)})
  end

  def user_not_authorized
    respond_to do |format|
      format.html do
        flash[:alert] = "You are not authorized to perform this action."
        redirect_back(fallback_location: root_path)
      end
      format.json { render json: { error: "forbidden" }, status: :forbidden }
      format.turbo_stream { head :forbidden }
    end
  end

  def record_not_found
    respond_to do |format|
      format.html { render file: Rails.root.join("public/404.html"), status: :not_found, layout: false }
      format.json { render json: { error: "not_found" }, status: :not_found }
    end
  end

  def set_sentry_context
    return unless defined?(Sentry) && current_user

    Sentry.set_user(id: current_user.id, email: current_user.email)
  end
end
