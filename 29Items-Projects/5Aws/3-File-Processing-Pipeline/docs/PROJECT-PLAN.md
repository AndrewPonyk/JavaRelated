# Project Plan: File Processing Pipeline

## 1.1 Project File Structure

The project uses a monorepo structure to keep frontend, backend, infra, and CI configurations grouped together logically.

```text
/
├── .github/                   # GitHub Actions CI/CD workflows
│   └── workflows/
│       └── deploy.yml         # Main pipeline for SAM and Vue.js
├── docs/                      # Project documentation (Architecture, Notes, Plan)
│   ├── ARCHITECTURE.md
│   ├── PROJECT-PLAN.md
│   └── TECH-NOTES.md
├── frontend/                  # Vue.js Frontend Application
│   ├── src/
│   │   ├── components/        # Reusable Vue components
│   │   ├── views/             # Page components
│   │   ├── services/          # API integration (Axios, etc.)
│   │   ├── store/             # Vuex/Pinia state management
│   │   └── App.vue            # Main App entrypoint
│   ├── public/                # Static assets
│   ├── Dockerfile             # Container definition for frontend (optional)
│   ├── package.json           # Frontend dependencies
│   └── vite.config.js         # Build tool config
├── backend/                   # AWS Lambda (Python) Backend
│   ├── src/
│   │   ├── api/               # API endpoints (Upload, Query, etc.)
│   │   ├── services/          # Business logic & AWS SDK integrations (Textract, Comprehend)
│   │   ├── models/            # Pydantic or basic data models
│   │   └── utils/             # Helper functions (logging, config parsing)
│   ├── tests/                 # Unit and integration tests (Pytest)
│   └── requirements.txt       # Python dependencies
├── infrastructure/            # Infrastructure as Code (AWS SAM)
│   ├── template.yaml          # Defines API Gateway, Lambdas, S3, SQS, DynamoDB
│   └── samconfig.toml         # SAM deployment variables
├── .env.example               # Environment variables template
├── .gitignore
└── README.md                  # Project overview
```

## 1.2 Implementation TODO List

### Phase 1: Foundation (High Priority)
- [ ] Initialize Git repository and structure standard monorepo folders.
- [ ] Setup AWS SAM template (`infrastructure/template.yaml`) defining basic S3 bucket, DynamoDB table, and basic IAM roles.
- [ ] Setup `.env.example` and local secrets management.
- [ ] Create stub Python Lambda functions (e.g., S3 upload trigger) to verify AWS permissions.
- [ ] Setup GitHub Actions workflow for linting code and basic testing.
- [ ] Initialize Vue.js (Vite) frontend project and install basic dependencies (Tailwind/Vuetify).

### Phase 2: Core Features (Medium Priority)
- [ ] **Backend:** Implement API endpoint for secure presigned URLs to upload directly to S3.
- [ ] **Backend:** Implement Lambda event handler for S3 `ObjectCreated` to push messages to SQS queue.
- [ ] **Backend:** Implement Textract integration in processing Lambda to extract text from PDF/images.
- [ ] **Backend:** Implement Comprehend integration to extract entities from OCR text.
- [ ] **Backend:** Store extracted data and entities in DynamoDB.
- [ ] **Frontend:** Create Upload Document component with progress bar.
- [ ] **Frontend:** Create Document List view polling or using WebSockets to show processing status.

### Phase 3: Polish & Optimization (Lower Priority)
- [ ] Implement custom ML model endpoint (SageMaker) integration for document classification.
- [ ] Add robust error handling and Dead Letter Queues (DLQ) for failed Textract/Comprehend processing.
- [ ] Setup AWS X-Ray and CloudWatch Alarms for monitoring failures.
- [ ] Setup full End-to-End (E2E) testing framework.
- [ ] Finalize production SAM deployment and Frontend CD pipeline to AWS S3/CloudFront.
