Rails.application.config.filter_parameters += %i[
  password password_confirmation api_token secret token key authorization cookie
  ssn credit_card
]
