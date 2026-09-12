# frozen_string_literal: true

module Api
  module V1
    class AnomaliesController < ApplicationController
      before_action :set_anomaly, only: %i[show update destroy resolve]

      def index
        anomalies = Anomaly.recent
        anomalies = anomalies.open if ActiveModel::Type::Boolean.new.cast(params[:open])
        anomalies, meta = paginated(anomalies)
        render json: { anomalies:, meta: }, status: :ok
      end

      def show
        render json: { anomaly: @anomaly }, status: :ok
      end

      def create
        anomaly = Anomaly.create!(anomaly_params)
        render json: { anomaly: }, status: :created
      end

      def update
        @anomaly.update!(anomaly_params)
        render json: { anomaly: @anomaly }, status: :ok
      end

      def destroy
        @anomaly.destroy!
        render json: { message: 'Anomaly deleted successfully' }, status: :ok
      end

      def resolve
        @anomaly.resolve!
        render json: { anomaly: @anomaly }, status: :ok
      end

      private

      def set_anomaly
        @anomaly = Anomaly.find(params[:id])
      end

      def anomaly_params
        params.require(:anomaly).permit(
          :metric_name,
          :severity,
          :description,
          :baseline_value,
          :observed_value,
          :detected_at,
          :resolved_at
        )
      end
    end
  end
end
