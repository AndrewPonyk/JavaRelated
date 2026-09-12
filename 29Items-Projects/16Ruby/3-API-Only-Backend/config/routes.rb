Rails.application.routes.draw do
  namespace :api do
    namespace :v1 do
      namespace :auth do
        post :register
        post :login
        delete :logout
        get :me
      end

      post 'sync', to: 'syncs#create'
      get 'sync/status', to: 'syncs#status'

      resources :users
      resources :sync_items
      resources :metrics
      resources :anomalies do
        member do
          patch :resolve
        end
      end
    end
  end

  get 'up' => 'rails/health#show', as: :rails_health_check
end
