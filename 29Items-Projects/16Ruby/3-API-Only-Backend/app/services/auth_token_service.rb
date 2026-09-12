# frozen_string_literal: true

class AuthTokenService
  class TokenError < StandardError; end

  def self.issue(user)
    token = JsonWebToken.encode({ sub: user.id, email: user.email }, expires_in:)
    payload = JsonWebToken.decode(token)
    { token:, expires_at: Time.at(payload.fetch('exp')).utc.iso8601 }
  end

  def self.decode(token)
    JsonWebToken.decode(token)
  rescue JsonWebToken::DecodeError => e
    raise TokenError, e.message
  end

  def self.revoke!(jti, expires_at)
    RevokedToken.find_or_create_by!(jti:) do |revoked_token|
      revoked_token.expires_at = expires_at
    end
  end

  def self.revoked?(jti)
    RevokedToken.active.exists?(jti:)
  end

  def self.expires_in
    ENV.fetch('JWT_EXPIRATION_HOURS', 24).to_i.hours
  end
  private_class_method :expires_in
end
