# frozen_string_literal: true

require_relative "../spec_helper"

RSpec.describe "API routes" do
  it "returns a health payload" do
    get "/health"

    expect(last_response).to be_ok
    expect(last_response.body).to include("ok")
  end

  it "redirects the preview root to the latest version landing page" do
    get "/"

    expect(last_response.status).to eq(302)
    expect(last_response["Location"]).to include("/docs/v2/getting-started")
  end

  it "lists documentation versions and pages" do
    get "/docs"

    expect(last_response).to be_ok
    expect(last_response.body).to include("Install")
    expect(last_response.body).to include("/docs/v2/install")
  end

  it "lists documentation pages for one version" do
    get "/docs/v2"

    expect(last_response).to be_ok
    expect(last_response.body).to include("Install")
    expect(last_response.body).not_to include("Version 1 guide")
  end

  it "validates create page input" do
    post "/api/pages", {}.to_json, "CONTENT_TYPE" => "application/json"

    expect(last_response.status).to eq(422)
    expect(last_response.body).to include("validation_failed")
  end

  it "returns paginated page lists" do
    get "/api/pages?limit=1&offset=1"

    payload = JSON.parse(last_response.body)
    expect(last_response).to be_ok
    expect(payload["total"]).to eq(3)
    expect(payload["pages"].size).to eq(1)
  end

  it "rejects malformed JSON with a structured error" do
    post "/api/pages", "{", "CONTENT_TYPE" => "application/json"

    expect(last_response.status).to eq(400)
    expect(last_response.body).to include("bad_request")
  end

  it "rejects write requests without JSON content type" do
    post "/api/pages", "{}", "CONTENT_TYPE" => "text/plain"

    expect(last_response.status).to eq(400)
    expect(last_response.body).to include("Content-Type must be application/json")
  end

  it "requires a write token in production" do
    previous_env = ENV["APP_ENV"]
    previous_token = ENV["API_AUTH_TOKEN"]
    ENV["APP_ENV"] = "production"
    ENV["API_AUTH_TOKEN"] = "secret-token"

    post "/api/build", "", "HTTP_AUTHORIZATION" => "Bearer wrong-token"

    expect(last_response.status).to eq(401)
  ensure
    ENV["APP_ENV"] = previous_env
    ENV["API_AUTH_TOKEN"] = previous_token
  end

  it "rejects invalid page identifiers before filesystem lookup" do
    get "/api/pages/x/secret"

    expect(last_response.status).to eq(422)
    expect(last_response.body).to include("Invalid page identifier")
  end

  it "rejects oversized search queries" do
    get "/api/search?q=#{"x" * 81}"

    expect(last_response.status).to eq(400)
    expect(last_response.body).to include("Search query is too long")
  end

  it "supports the full page CRUD API" do
    payload = {
      title: "API Guide",
      slug: "api-guide",
      version: "v2",
      description: "API documentation",
      tags: ["api"],
      body: "# API Guide\n\nSearchable API docs."
    }

    post "/api/pages", payload.to_json, "CONTENT_TYPE" => "application/json"
    expect(last_response.status).to eq(201)

    get "/api/pages/v2/api-guide"
    expect(last_response).to be_ok
    expect(last_response.body).to include("Searchable API docs")

    put "/api/pages/v2/api-guide", { title: "API Reference" }.to_json, "CONTENT_TYPE" => "application/json"
    expect(last_response).to be_ok
    expect(last_response.body).to include("API Reference")

    delete "/api/pages/v2/api-guide"
    expect(last_response).to be_ok
    expect(last_response.body).to include("deleted")
  end

  it "returns search and version responses" do
    post "/api/build", {}.to_json, "CONTENT_TYPE" => "application/json"
    expect(last_response).to be_ok

    get "/api/search?q=install"
    expect(last_response).to be_ok
    expect(last_response.body).to include("install")

    get "/api/versions"
    expect(last_response).to be_ok
    expect(last_response.body).to include("v2")
  end
end
