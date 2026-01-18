Phase 3: Production Polish ✨

Audit your Phase 2 implementation and make it production-ready.

**Note:** Apply checks that are relevant to YOUR project structure and tech stack.

---

## 1. Code Quality
- [ ] Clean code: no debug statements, commented blocks, unused imports
- [ ] No hardcoded secrets or credentials
- [ ] Consistent formatting and naming
- [ ] Functions/methods are focused (single responsibility)

## 2. Bug Fixes
- [ ] SQL injection → use parameterized queries (if using database)
- [ ] XSS/CSRF → sanitize inputs, validate everything
- [ ] Null/undefined handling everywhere
- [ ] Proper error responses (400, 401, 404, 500 - if API)
- [ ] Edge cases: empty inputs, invalid formats, boundaries

## 3. Performance
- [ ] Database indexes (foreign keys, search fields - if applicable)
- [ ] Fix N+1 queries (if applicable)
- [ ] API pagination (if returning lists)
- [ ] Response compression (if web service)

## 4. Security
- [ ] Passwords hashed (bcrypt/Argon2 - if handling auth)
- [ ] Secrets in environment variables (never in code)
- [ ] Input validation on all endpoints
- [ ] Update dependencies (fix vulnerabilities)
- [ ] HTTPS enforced (if web-facing)

## 5. Testing
- [ ] Test coverage ≥ 80%
- [ ] All tests passing
- [ ] Integration tests for main flows
- [ ] Test error scenarios

## 6. Documentation
- [ ] README: complete setup instructions
- [ ] API docs: all endpoints with examples (if API exists)
- [ ] `.env.example` has all variables (if using env vars)
- [ ] Troubleshooting section

## 7. Build & Startup Verification
- [ ] Project builds without errors
- [ ] All dependencies declared in package.json/pom.xml/requirements.txt/go.mod
- [ ] All imports exist (no missing modules)
- [ ] Entry point file correct (main.py, index.js, Application.java, main.go)
- [ ] Configuration files valid (no syntax errors in .yml, .json, .env.example)
- [ ] Docker setup: Dockerfile and docker-compose.yml valid (if using Docker)
- [ ] Health check endpoint implemented (if web service)
- [ ] CI/CD pipeline config valid (if you created one)
- [ ] Database migrations valid syntax (if using database)

---

## Final Check

Before finishing, verify what YOU implemented:
- [ ] Build succeeds (no compilation/syntax errors)
- [ ] All tests pass (80%+ coverage)
- [ ] Startup prerequisites met (deps, configs, entry point)
- [ ] Main user flow works end-to-end
- [ ] No secrets in code
- [ ] README instructions are accurate

**Fix and polish everything you created. Make it production-ready.**