# frozen_string_literal: true

class SyncService
  class InvalidChange < StandardError; end

  Result = Data.define(:status, :applied, :conflicts, :server_changes, :last_sync_at) do
    def as_json(*)
      {
        status:,
        applied:,
        conflicts:,
        server_changes:,
        last_sync_at: last_sync_at.iso8601
      }
    end
  end

  def initialize(user:, changes: [], since: nil)
    @user = user
    @changes = Array(changes)
    @since = parse_time(since)
    @now = Time.current
  end

  def call
    applied = []
    conflicts = []

    SyncItem.transaction do
      changes.each do |change|
        item_result = apply_change(change)
        if item_result[:conflict]
          conflicts << item_result[:conflict]
        else
          applied << item_result.fetch(:item).serializable_hash
        end
      end
    end

    Result.new(
      status: conflicts.empty? ? 'success' : 'conflict',
      applied:,
      conflicts:,
      server_changes: server_changes(applied),
      last_sync_at: now
    )
  end

  private

  attr_reader :user, :changes, :since, :now

  def apply_change(raw_change)
    change = normalize_change(raw_change)
    item = user.sync_items.find_or_initialize_by(
      collection_name: change.fetch(:collection_name),
      record_id: change.fetch(:record_id)
    )

    if conflict?(item, change)
      return {
        conflict: {
          collection_name: item.collection_name,
          record_id: item.record_id,
          client_payload: change.fetch(:payload),
          server_item: item.serializable_hash
        }
      }
    end

    item.assign_attributes(
      payload: change.fetch(:payload),
      client_updated_at: change.fetch(:client_updated_at),
      last_synced_at: now,
      deleted_at: change.fetch(:deleted) ? now : nil
    )
    item.save!
    { item: }
  end

  def normalize_change(raw_change)
    change_hash = raw_change.respond_to?(:to_unsafe_h) ? raw_change.to_unsafe_h : raw_change.to_h
    change = change_hash.with_indifferent_access
    collection_name = change[:collection_name] || change[:collection]
    record_id = change[:record_id] || change[:id]
    payload = change[:payload]

    raise InvalidChange, 'collection_name is required' if collection_name.blank?
    raise InvalidChange, 'record_id is required' if record_id.blank?
    raise InvalidChange, 'payload must be an object' unless payload.is_a?(Hash)

    {
      collection_name: collection_name.to_s,
      record_id: record_id.to_s,
      payload:,
      client_updated_at: parse_time(change[:client_updated_at] || change[:updated_at]) || now,
      deleted: ActiveModel::Type::Boolean.new.cast(change[:deleted])
    }
  end

  def conflict?(item, change)
    return false unless item.persisted?
    return false if item.client_updated_at <= change.fetch(:client_updated_at)

    item.payload != change.fetch(:payload) || item.deleted?
  end

  def server_changes(applied)
    applied_ids = applied.filter_map { |item| item['id'] || item[:id] }
    scope = user.sync_items.recent
    scope = scope.where.not(id: applied_ids) if applied_ids.any?
    scope = scope.changed_since(since) if since
    scope.limit(100).map(&:serializable_hash)
  end

  def parse_time(value)
    return if value.blank?

    Time.zone.parse(value.to_s)
  rescue ArgumentError, TypeError
    raise InvalidChange, "Invalid timestamp: #{value}"
  end
end
