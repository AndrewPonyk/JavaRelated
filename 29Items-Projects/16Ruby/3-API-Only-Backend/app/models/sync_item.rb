# frozen_string_literal: true

class SyncItem < ApplicationRecord
  COLLECTION_NAME = /\A[a-z][a-z0-9_]{1,63}\z/

  belongs_to :user

  validates :collection_name, presence: true, format: { with: COLLECTION_NAME }
  validates :record_id, presence: true
  validates :payload, presence: true
  validates :client_updated_at, presence: true
  validates :record_id, uniqueness: { scope: %i[user_id collection_name] }

  scope :active, -> { where(deleted_at: nil) }
  scope :changed_since, ->(time) { where('last_synced_at > ? OR updated_at > ?', time, time) }
  scope :recent, -> { order(last_synced_at: :desc) }

  before_validation :ensure_payload
  before_validation :ensure_sync_timestamp

  def deleted?
    deleted_at.present?
  end

  def serializable_hash(options = nil)
    super(
      {
        only: %i[id collection_name record_id payload client_updated_at last_synced_at deleted_at created_at
                 updated_at],
        methods: %i[deleted?]
      }.merge(options || {})
    )
  end

  private

  def ensure_payload
    self.payload = {} if payload.blank?
  end

  def ensure_sync_timestamp
    self.client_updated_at ||= Time.current
    self.last_synced_at ||= Time.current
  end
end
