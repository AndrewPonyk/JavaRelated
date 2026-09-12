# frozen_string_literal: true

class JsonWebToken
  class DecodeError < StandardError; end

  ALGORITHM = 'HS256'

  def self.encode(payload, expires_in: 24.hours)
    now = Time.current.to_i
    claims = payload.stringify_keys.merge(
      'iat' => now,
      'exp' => expires_in.from_now.to_i,
      'jti' => SecureRandom.uuid
    )

    header = { alg: ALGORITHM, typ: 'JWT' }
    signing_input = [base64_json(header), base64_json(claims)].join('.')
    [signing_input, sign(signing_input)].join('.')
  end

  def self.decode(token)
    encoded_header, encoded_payload, signature = token.to_s.split('.')
    raise DecodeError, 'Malformed token' unless encoded_header && encoded_payload && signature

    signing_input = [encoded_header, encoded_payload].join('.')
    expected_signature = sign(signing_input)
    raise DecodeError, 'Invalid token signature' unless secure_compare(signature, expected_signature)

    payload = JSON.parse(base64_decode(encoded_payload))
    raise DecodeError, 'Token has expired' if Time.zone.at(payload.fetch('exp')) <= Time.current

    payload
  rescue JSON::ParserError, KeyError
    raise DecodeError, 'Invalid token payload'
  end

  def self.base64_json(value)
    Base64.urlsafe_encode64(value.to_json, padding: false)
  end
  private_class_method :base64_json

  def self.base64_decode(value)
    Base64.urlsafe_decode64(value)
  rescue ArgumentError
    raise DecodeError, 'Invalid token encoding'
  end
  private_class_method :base64_decode

  def self.sign(value)
    digest = OpenSSL::HMAC.digest('SHA256', secret, value)
    Base64.urlsafe_encode64(digest, padding: false)
  end
  private_class_method :sign

  def self.secret
    ENV['JWT_SECRET_KEY'].presence || Rails.application.secret_key_base
  end
  private_class_method :secret

  def self.secure_compare(left, right)
    ActiveSupport::SecurityUtils.secure_compare(left, right)
  rescue ArgumentError
    false
  end
  private_class_method :secure_compare
end
