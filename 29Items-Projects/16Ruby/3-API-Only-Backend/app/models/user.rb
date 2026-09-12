# frozen_string_literal: true

class User < ApplicationRecord
  has_secure_password

  has_many :sync_items, dependent: :destroy
  has_many :api_metrics, dependent: :nullify

  before_validation :normalize_email
  before_validation :ensure_jti

  validates :email, presence: true, uniqueness: { case_sensitive: false },
                    format: { with: URI::MailTo::EMAIL_REGEXP }
  validates :password, length: { minimum: 8 }, allow_nil: true
  validates :jti, presence: true, uniqueness: true

  def serializable_hash(options = nil)
    super({ only: %i[id email created_at updated_at] }.merge(options || {}))
  end

  private

  def normalize_email
    self.email = email.to_s.strip.downcase
  end

  def ensure_jti
    self.jti ||= SecureRandom.uuid
  end
end
