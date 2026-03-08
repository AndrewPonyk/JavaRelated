# File Processing Pipeline

## Complete Application Setup

This repository contains a fully functional end-to-end File Processing Pipeline using Vue.js for the frontend and AWS Serverless components (API Gateway, Lambda, SQS, DynamoDB, Textract, Comprehend) for the backend.

> **Note:** The SageMaker integration for custom document classification is currently missing. The pipeline extracts text and entities but does not yet classify document types using a custom ML model.

### Prerequisites
- Node.js 18+ (for frontend development)
- Python 3.9+ (for backend development)
- Docker Desktop (for localstack execution)
- AWS SAM CLI (for deployment)

### Running Locally (Docker Compose)
You can run the frontend application locally pointing to LocalStack to simulate AWS services. Note that LocalStack's free version doesn't fully support Textract/Comprehend, so tests focus on the primary flow (API Gateway, DynamoDB, S3, SQS).

```bash
docker-compose up -d
```
The frontend will be available at `http://localhost:5173`.

### Running Tests
To run tests locally:

**Backend Tests:**
```bash
cd backend
pip install -r requirements.txt
pytest
```

**Frontend Tests:**
```bash
cd frontend
npm install
npm test
```

### Deployment to AWS

Yes, deploying the entire infrastructure (including frontend hosting buckets) is exactly that simple. AWS Serverless Application Model (SAM) allows us to define Infrastructure as Code seamlessly.

1. Navigate to the infrastructure folder.
2. Build the SAM application:
```bash
sam build --template-file infrastructure/template.yaml
```
3. Deploy to AWS:
```bash
sam deploy --guided
```

This command will provision:
- API Gateway endpoints
- SQS Queues and Dead Letter Queues
- AWS Lambdas with managed IAM execution roles (permissions to Textract/Comprehend generated dynamically)
- DynamoDB Tables
- Raw documents S3 Bucket
- A public S3 bucket configured for Static Website Hosting for the frontend

Once it finishes, it will print out the `FrontendBucketName`. You can then copy your built frontend `dist` directory straight into it!

> **Note:** Continuous Integration and Deployment are configured directly inside `.github/workflows/main.yml`. Merging natively into `main` will automatically build your Vue.js application and deploy the new frontend artifacts directly into the SAM-provisioned website bucket.
