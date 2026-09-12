Rails.application.routes.draw do
  devise_for :users, controllers: { omniauth_callbacks: "users/omniauth_callbacks" }

  root "projects#index"

  get "/health", to: "health#show"

  resources :projects do
    resources :sprints
    resources :issues do
      resources :time_entries, only: %i[create destroy]
    end
    get "board", to: "boards#show"
  end

  namespace :api do
    namespace :v1 do
      resources :projects, only: [] do
        resources :issues
      end
    end
  end

  require "sidekiq/web"
  authenticate :user, ->(u) { u.admin? } do
    mount Sidekiq::Web => "/sidekiq"
  end
end
