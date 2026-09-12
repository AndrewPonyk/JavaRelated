module RequestHelpers
  def auth_headers(user)
    {
      "Authorization" => "Bearer #{Auth::TokenService.access_token_for(user)}",
      "Content-Type" => "application/json"
    }
  end

  def json_body
    JSON.parse(response.body)
  end
end
