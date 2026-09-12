# frozen_string_literal: true

class RevokedToken < ApplicationRecord
  validates :jti, presence: true, uniqueness: true
  validates :expires_at, presence: true

  scope :active, -> { where('expires_at > ?', Time.current) }

  def self.cleanup_expired!
    where(expires_at: ..Time.current).delete_all
  end
end
