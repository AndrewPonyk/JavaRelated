class GraphqlController < ApplicationController
  def execute
    result = SocialNetworkSchema.execute(
      params[:query],
      variables: variables,
      context: { current_user: current_user },
      operation_name: params[:operationName]
    )

    render json: result
  rescue StandardError => e
    Rails.logger.error({ message: e.message, backtrace: e.backtrace&.first(5) }.to_json)
    render json: { errors: [{ message: "Internal server error" }] }, status: :internal_server_error
  end

  private

  def variables
    case params[:variables]
    when String
      params[:variables].present? ? JSON.parse(params[:variables]) : {}
    when Hash, ActionController::Parameters
      params[:variables].to_unsafe_h
    else
      {}
    end
  end
end
