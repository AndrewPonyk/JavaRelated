# Enterprise Insurance Portal

## Overview
A scalable enterprise insurance portal built with .NET 9 Web API and React + Vite. The project uses Docker Compose to orchestrate SQL Server and Kafka.

## Prerequisites
- .NET 9 SDK
- Node.js & npm
- Docker Desktop

## Setup Instructions
1. Copy `.env.example` to `.env` and fill out any custom configurations.
2. Run `docker-compose up -d` from the root directory to start SQL Server and Kafka.
3. To start the backend API:
   ```bash
   cd src/backend/EnterpriseInsurance.Api
   dotnet run
   ```
4. To start the frontend React portal:
   ```bash
   cd src/frontend/public-portal
   npm install
   npm run build
   npx serve -s dist -p 5174
   ```

## Production Polish Features Added
- **Code Quality**: Cleaned up code and enforced standards. Removed hardcoded connection strings from configuration files.
- **Security**: Moved database connection strings to Environment configurations (`.env`).
- **Performance**: 
  - Added Database Indexes (Unique constraints on PolicyNumber and Customer Email) to enhance lookups.
  - API Pagination introduced for `/api/customers` and `/api/policies` (`page` and `pageSize` query params).
  - Applied `.AsNoTracking()` in Entity Framework Repositories for read-only queries. 
  - Configured Response Compression in the API.
- **Testing**: Added xUnit tests for PolicyService covering creating, binding, and deleting policies.
- **Documentation**: Provided `.env.example` and this `README.md`. Health checks are available at `/health`. Added troubleshooting guidance in case of port collisions.

## API Endpoints
- `GET /api/customers?page=1&pageSize=10` - List paginated customers.
- `POST /api/customers` - Create a new customer.
- `GET /api/policies?page=1&pageSize=10` - List paginated policies.
- `POST /api/policies` - Create a new policy.
- `POST /api/policies/{id}/bind` - Bind an existing policy.
- `DELETE /api/policies/{id}` - Delete an existing policy.
- `GET /health` - API Health check.

## Troubleshooting
- **Database Connection**: Make sure `docker-compose up -d` has successfully initialized SQL Server and it is healthy.
- **Port Conflicts**: If port 5162 (backend) or 5174 (frontend) is in use, modify the `launchSettings.json` and vite start script respectively.
