class User < ApplicationRecord
  has_secure_password

  has_many :posts, dependent: :destroy
  has_many :messages, foreign_key: :sender_id, dependent: :destroy, inverse_of: :sender
  has_many :received_messages, class_name: "Message", foreign_key: :recipient_id, dependent: :destroy, inverse_of: :recipient
  has_many :notifications, dependent: :destroy

  has_many :follower_relationships,
           class_name: "Follow",
           foreign_key: :followee_id,
           dependent: :destroy,
           inverse_of: :followee
  has_many :following_relationships,
           class_name: "Follow",
           foreign_key: :follower_id,
           dependent: :destroy,
           inverse_of: :follower

  normalizes :email, with: ->(email) { email.strip.downcase }
  normalizes :username, with: ->(username) { username.strip.downcase }

  validates :email, presence: true, uniqueness: true, format: { with: URI::MailTo::EMAIL_REGEXP }
  validates :username, presence: true, uniqueness: true, length: { in: 3..32 }, format: { with: /\A[a-z0-9_]+\z/ }
  validates :display_name, length: { maximum: 80 }, allow_blank: true
  validates :password, length: { minimum: 8 }, allow_nil: true

  def following?(other_user)
    following_relationships.exists?(followee_id: other_user.id)
  end

  def issue_refresh_token!
    token = SecureRandom.urlsafe_base64(48)
    update!(
      refresh_token_digest: BCrypt::Password.create(token),
      refresh_token_expires_at: 30.days.from_now
    )
    token
  end

  def authenticate_refresh_token(token)
    return false if refresh_token_digest.blank? || refresh_token_expires_at.blank?
    return false if refresh_token_expires_at.past?

    BCrypt::Password.new(refresh_token_digest).is_password?(token)
  end

  def revoke_refresh_token!
    update!(refresh_token_digest: nil, refresh_token_expires_at: nil)
  end
end
