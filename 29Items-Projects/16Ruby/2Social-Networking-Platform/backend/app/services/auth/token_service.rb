module Auth
  class TokenService
    ISSUER = "social-networking-platform".freeze
    ACCESS_TOKEN_TTL = 15.minutes

    class << self
      def access_token_for(user)
        payload = {
          sub: user.id,
          iss: ISSUER,
          exp: ACCESS_TOKEN_TTL.from_now.to_i,
          iat: Time.current.to_i
        }

        JWT.encode(payload, secret, "HS256")
      end

      def user_from_token(token)
        payload, = JWT.decode(token, secret, true, { algorithm: "HS256", iss: ISSUER, verify_iss: true })
        User.find(payload.fetch("sub"))
      rescue JWT::DecodeError, ActiveRecord::RecordNotFound
        nil
      end

      private

      def secret
        ENV.fetch("JWT_SECRET_KEY_BASE") { Rails.application.secret_key_base }
      end
    end
  end
end
