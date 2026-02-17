# Enterprise Content Management System

A robust, scalable CMS built with PHP 8.3, Laravel 11, and React. Features include multi-site versioning, ML-based content recommendations, and full-text search via Elasticsearch.

## 🌟 Functional Overview

This CMS is designed to serve two primary user experiences:

### 1. Public Visitor (Frontend)
Accessible at the root URL (`/`), the public site offers a clean, performant reading experience.
- **Home Page**: A curated feed of the latest *Published* articles. Draft or Archived content is automatically hidden.
- **Article Reader**: A distraction-free reading view for individual articles.
  - **Slug Support**: Articles are accessible via SEO-friendly URLs (e.g., `/article/my-new-post`) or by ID.
  - **Full Content**: Displays the complete article text, author bio, read time estimation, and publication date.
- **Dynamic Branding**: The site title and branding elements are dynamically fetched from the backend configuration.

### 2. Administrator (Content Management)
Accessible via (`/admin`), providing tools to manage content and site configuration.
- **Content Dashboard**: 
  - View all articles regardless of status (Draft, Published, Archived).
  - Quick status indicators to track content lifecycle.
- **Article Editor**:
  - **Create/Edit**: rigorous inputs for Title, Slug, Content, and Status.
  - **Status Workflow**: Switch articles between `Draft` (hidden), `Published` (live), and `Archived`.
- **Global Settings**:
  - **Workflow Settings**: Configure global site parameters (like "Site Name") that instantly update the public-facing frontend without code deployment.

## 🚀 Getting Started

Follow these steps to set up the project locally.

### Prerequisites
- **Docker** & **Docker Compose**
- **PHP 8.3+** (for local CLI commands)
- **Composer**
- **Node.js & NPM**

### Installation

1.  **Install PHP Dependencies**
    If running locally on Windows/Mac without PHP extensions, ignore platform requirements:
    ```bash
    composer install --ignore-platform-req=ext-fileinfo --ignore-platform-req=ext-zip
    ```

2.  **Environment Setup**
    Copy the example environment file and configure it (defaults are set for Docker).
    ```bash
    cp .env.example .env
    ```

3.  **Generate Application Key**
    ```bash
    php artisan key:generate
    ```

4.  **Start Services (Docker)**
    Start MySQL, Redis, and Elasticsearch containers.
    ```bash
    docker-compose -f docker/docker-compose.yml up -d --build
    ```

5.  **Database Migration (Inside Docker)**
    Create the database structure from within the running container to ensure connectivity.
    ```bash
    docker-compose -f docker/docker-compose.yml exec app php artisan migrate:fresh
    ```

6.  **Frontend Setup**
    Install dependencies and build assets.
    ```bash
    npm install
    npm run dev
    ```

## 🏗 Architecture
See `docs/ARCHITECTURE.md` for detailed architectural diagrams and decisions.
- **Backend**: Laravel 11 (API-first)
- **Frontend**: React + Tailwind CSS
- **Database**: MySQL 8.0
- **Cache/Queue**: Redis
- **Search**: Elasticsearch

## ✨ Features

### Dynamic Site Configuration
The site title and other global settings are configurable directly from the Admin Panel, without needing to redeploy or touch environment files.
- **Admin**: Go to `Admin -> Workflow Settings` to update the Site Name.
- **Frontend**: The public-facing site automatically reflects these changes.
- **Storage**: Settings are stored in the `settings` database table.

## 🗄 Database Management

### Running Migrations
To apply new database changes (like the settings table) without losing data:
```bash
docker exec cms-app php artisan migrate
```

### Resetting Database
To wipe all data and start fresh:
```bash
docker exec cms-app php artisan migrate:fresh --seed
```

## 🧪 Testing
Run the test suite:
```bash
php artisan test
```

## 📝 Documentation
- [Project Plan](docs/PROJECT-PLAN.md)
- [Architecture Overview](docs/ARCHITECTURE.md)
- [Technical Notes](docs/TECH-NOTES.md)
