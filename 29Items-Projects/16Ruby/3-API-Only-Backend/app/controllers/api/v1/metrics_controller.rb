# frozen_string_literal: true

module Api
  module V1
    class MetricsController < ApplicationController
      before_action :set_metric, only: %i[show update destroy]

      def index
        metrics, meta = paginated(current_user.api_metrics.recent)
        render json: { metrics:, meta: }, status: :ok
      end

      def show
        render json: { metric: @metric }, status: :ok
      end

      def create
        metric = ApiMetric.create!(metric_params)
        AnomalyDetectionJob.perform_later
        render json: { metric: }, status: :created
      end

      def update
        @metric.update!(metric_params)
        render json: { metric: @metric }, status: :ok
      end

      def destroy
        @metric.destroy!
        render json: { message: 'Metric deleted successfully' }, status: :ok
      end

      private

      def set_metric
        @metric = ApiMetric.find(params[:id])
      end

      def metric_params
        permitted = params.require(:metric).permit(
          :user_id,
          :method,
          :path,
          :status,
          :latency_ms,
          :occurred_at,
          metadata: {}
        )
        permitted[:user_id] = current_user.id
        permitted
      end
    end
  end
end
