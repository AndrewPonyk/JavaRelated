module ApplicationCable
  class Connection < ActionCable::Connection::Base
    identified_by :current_user

    def connect
      self.current_user = find_verified_user
    end

    private

    def find_verified_user
      verified = env["warden"]&.user || authenticate_via_token
      return verified if verified

      reject_unauthorized_connection
    end

    def authenticate_via_token
      header_token = request.headers["Authorization"].to_s.sub(/\ABearer /, "")
      token = header_token.presence || request.params[:token]
      User.find_by(api_token: token) if token.present?
    end
  end
end
