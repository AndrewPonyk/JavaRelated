# frozen_string_literal: true

module Api
  module V1
    class SyncsController < ApplicationController
      rescue_from SyncService::InvalidChange do |error|
        render_error('invalid_sync_change', error.message, :unprocessable_content)
      end

      def create
        result = SyncService.new(
          user: current_user,
          changes: params[:changes] || [],
          since: params[:since]
        ).call

        render json: result.as_json, status: result.status == 'success' ? :ok : :conflict
      end

      def status
        last_sync = current_user.sync_items.maximum(:last_synced_at)
        total_records = current_user.sync_items.active.count
        deleted_records = current_user.sync_items.where.not(deleted_at: nil).count

        render json: {
          lastSyncAt: last_sync&.iso8601,
          status: last_sync.present? ? 'success' : 'pending',
          totalRecords: total_records,
          deletedRecords: deleted_records
        }, status: :ok
      end
    end
  end
end
