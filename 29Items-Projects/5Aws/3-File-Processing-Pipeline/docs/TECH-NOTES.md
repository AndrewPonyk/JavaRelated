# Technical Notes

## 3.1 CI/CD Pipeline Design
- **Linting:** Run `eslint` (Vue.js) and `flake8` / `black` (Python).
- **Testing:** Execute `pytest` for backend unit tests and `vitest` / `jest` for frontend components.
- **Building:** Build Vue.js artifacts (`npm run build`). SAM build for AWS Lambda deployment packages.
- **Deploying:** 
  - **Dev:** Deploys automatically on merge to `develop` branch.
  - **Prod:** Requires manual approval on merge to `main`. Deploys via `sam deploy` and syncing S3 frontend bucket.

## 3.2 Testing Strategy
- **Unit Testing Frameworks:** `pytest` for Python Lambdas, mocking `boto3` using `moto`. `vitest` + Vue Test Utils for frontend testing. Target 80% coverage on core business logic.
- **Integration Testing:** Tests validating the interaction between API Gateway and DynamoDB locally using tools like LocalStack or DynamoDB Local.
- **End-to-End (E2E) Testing:** Cypress or Playwright to simulate user login, document upload, and result retrieval in a staging environment.

## 3.3 Deployment Strategy
- **Infrastructure:** AWS Serverless Application Model (SAM) allows declarative Infrastructure as Code.
- **Frontend:** Single Page Application (SPA) deployed to AWS S3 and served via CloudFront CDN. Containers are not strictly necessary unless SSR (Nuxt.js) is introduced later. If Docker is preferred for local testing, standard multi-stage builds are recommended.

## 3.4 Environment Management
- Environments are strictly separated by AWS accounts or at minimum SAM prefixes (e.g., `dev-Pipeline`, `prod-Pipeline`).
- Environment variables are managed by SAM `Parameters` for Lambdas and `.env` files locally.

## 3.5 Version Control Workflow
- **Gitflow** is recommended due to the clear staging environments needed.
  - `main` branch: Stable production code.
  - `develop` branch: Integration branch for next release.
  - `feature/*` branches: Scoped tasks branched off from `develop`.
- PR reviews are strictly enforced before merging to `develop`.

## 3.6 Common Pitfalls
- **Lambda Timeouts:** Textract processing can take > 15 minutes for large PDFs, exceeding the 15-minute Lambda max execution time. **Solution:** Use Asynchronous Textract API (StartDocumentTextExtraction) with SNS triggers for large documents.
- **IAM Permissions:** Very common to miss subtle permissions across S3, SQS, KMS, and Textract. Keep a strict least-privilege IAM policy generation process.
- **CORS Issues:** Browsers rejecting pre-signed URL uploads. **Solution:** Explicitly configure CORS rules on the S3 raw documents bucket.
