# Static Site Generator - Technical Notes

## 3.1 CI/CD Pipeline Design

Recommended GitHub Actions stages:

1. **Install**: set up Ruby 3.3 and install bundled gems.
2. **Lint**: run RuboCop against application, scripts, and specs.
3. **Test**: run RSpec unit and route tests.
4. **Build**: generate the static site into `build/`.
5. **Validate**: check generated HTML, search JSON, links, redirects, sitemap, feed, and required metadata.
6. **Deploy staging**: upload non-main branches or tagged prereleases to a staging S3 prefix.
7. **Deploy production**: deploy from `main` or release tags to the production S3 bucket and invalidate CloudFront.

Keep deployment jobs protected with GitHub environments so production requires review when appropriate.

## 3.2 Testing Strategy

### Unit Tests

- Use RSpec.
- Target high coverage for `MarkdownParser`, `TemplateRenderer`, `VersionIndex`, `SearchIndexer`, and `KeywordExtractor`.
- Keep parser tests fixture-based so front matter edge cases are easy to verify.
- Aim for at least 80% meaningful service-layer coverage early, then raise coverage around build orchestration as behavior stabilizes.

### Integration Tests

- Test a small content tree from Markdown input to generated HTML and search JSON.
- Validate that versioned paths, canonical URLs, and navigation links are generated correctly.
- Test deployment service behavior with mocked AWS SDK clients.
- Test SQLite-backed page CRUD with isolated fixture content and temporary databases.

### End-to-End Tests

- Use Playwright or Capybara only if the preview UI grows beyond simple server-rendered pages.
- Test the search UI against `public/search/index.json`.
- Include accessibility checks for generated templates before production releases.

## 3.3 Deployment Strategy

Production deployment should be static:

- Build files into `build/`.
- Upload HTML with short cache lifetimes.
- Upload CSS, JavaScript, and images with longer cache lifetimes when filenames are fingerprinted.
- Upload `search/index.json` with a short or moderate cache lifetime.
- Invalidate CloudFront paths after production deployments.

Containerization is useful for repeatable builds and local previews, but the production runtime should remain S3 plus CloudFront. The provided `Dockerfile` supports running the Sinatra preview app and build scripts in a controlled Ruby 3.3 environment.

Run the full local pipeline with `bundle exec rake`, or in containers with `docker-compose run --rm test`.

## 3.4 Environment Management

Use environment variables for deployment-specific and secret values. Keep non-secret defaults in `config/settings.yml` and environment overrides in `config/environments.yml`.

Required `.env.example` variables:

```bash
APP_ENV=development
SITE_NAME=Static Site Generator
SITE_BASE_URL=http://localhost:4567
AWS_REGION=us-east-1
AWS_S3_BUCKET=example-docs-bucket
AWS_ACCESS_KEY_ID=replace-me
AWS_SECRET_ACCESS_KEY=replace-me
CLOUDFRONT_DISTRIBUTION_ID=replace-me
BUILD_DIR=build
CONTENT_DIR=content/docs
LOG_LEVEL=info
API_AUTH_TOKEN=
```

Never commit `.env`.

## 3.5 Version Control Workflow

Use **GitHub Flow**:

- `main` is always deployable.
- Feature work happens in short-lived branches.
- Pull requests run linting, tests, and build validation.
- Merges to `main` deploy to production after environment approval.

This workflow fits a static documentation generator because changes are usually small, reviewable, and easy to validate through generated artifacts.

## 3.6 Common Pitfalls

- YAML front matter can silently produce unexpected Ruby types. Validate required fields and normalize dates, booleans, and arrays.
- ERB templates can become unsafe if untrusted content is rendered without escaping.
- Markdown libraries differ in fenced code block, table, and heading ID behavior. Pin parser options and test them.
- S3 cache-control mistakes can make stale HTML or search results persist after deployment.
- CloudFront invalidations can become expensive if every deploy invalidates `/*`. Prefer targeted invalidations when possible.
- Large client-side search indexes can slow down page load. Store only fields needed by the UI.
- Versioned docs can produce duplicate canonical URLs unless version metadata is explicit.
- Local preview behavior can drift from static output if routes bypass the same rendering services used by the build.
