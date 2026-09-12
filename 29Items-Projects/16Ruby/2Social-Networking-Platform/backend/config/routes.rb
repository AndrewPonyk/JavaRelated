Rails.application.routes.draw do
  get "/cable", to: proc { [204, { "Content-Type" => "text/plain" }, []] }
  post "/graphql", to: "graphql#execute"

  namespace :api do
    post "/auth/register", to: "auth#register"
    post "/auth/login", to: "auth#login"
    post "/auth/refresh", to: "auth#refresh"
    delete "/auth/logout", to: "auth#logout"
    get "/auth/me", to: "auth#me"

    resources :users
    resources :posts, only: %i[index show create update destroy]
    resources :follows
    resources :messages
    resources :notifications
    resources :toxicity_results
    get "/search", to: "search#index"
  end

  get "/health", to: proc { [200, { "Content-Type" => "application/json" }, ['{"status":"ok"}']] }
end
