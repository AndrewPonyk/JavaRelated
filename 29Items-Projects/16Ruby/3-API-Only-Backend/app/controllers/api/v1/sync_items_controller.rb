# frozen_string_literal: true

module Api
  module V1
    class SyncItemsController < ApplicationController
      before_action :set_sync_item, only: %i[show update destroy]

      def index
        items = current_user.sync_items.recent
        items = items.where(collection_name: params[:collection_name]) if params[:collection_name].present?
        items, meta = paginated(items)
        render json: { sync_items: items, meta: }, status: :ok
      end

      def show
        render json: { sync_item: @sync_item }, status: :ok
      end

      def create
        item = current_user.sync_items.create!(sync_item_params)
        render json: { sync_item: item }, status: :created
      end

      def update
        @sync_item.update!(sync_item_params)
        render json: { sync_item: @sync_item }, status: :ok
      end

      def destroy
        @sync_item.update!(deleted_at: Time.current, last_synced_at: Time.current)
        render json: { sync_item: @sync_item }, status: :ok
      end

      private

      def set_sync_item
        @sync_item = current_user.sync_items.find(params[:id])
      end

      def sync_item_params
        params.require(:sync_item).permit(
          :collection_name,
          :record_id,
          :client_updated_at,
          :last_synced_at,
          :deleted_at,
          payload: {}
        )
      end
    end
  end
end
