param(
  [string]$GraphqlUrl = "http://localhost:3000/graphql"
)

$healthBody = @{ query = "{ health }" } | ConvertTo-Json
$health = Invoke-RestMethod -Method Post -Uri $GraphqlUrl -ContentType "application/json" -Body $healthBody
if ($health.data.health -ne "ok") {
  throw "GraphQL health check failed"
}

$loginBody = @{
  query = "mutation Login(`$email: String!, `$password: String!) { loginUser(email: `$email, password: `$password) { authPayload { accessToken user { username } } errors } }"
  variables = @{
    email = "demo@example.com"
    password = "password123"
  }
} | ConvertTo-Json -Depth 5

$login = Invoke-RestMethod -Method Post -Uri $GraphqlUrl -ContentType "application/json" -Body $loginBody
if (-not $login.data.loginUser.authPayload.accessToken) {
  throw "Demo login smoke test failed: $($login.data.loginUser.errors -join ', ')"
}

$login.data.loginUser.authPayload.user
