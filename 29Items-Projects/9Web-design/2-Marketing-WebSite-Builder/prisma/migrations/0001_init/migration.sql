CREATE TYPE "Role" AS ENUM ('OWNER', 'DEVELOPER', 'MARKETER', 'REVIEWER', 'VIEWER');
CREATE TYPE "SiteStatus" AS ENUM ('DRAFT', 'PUBLISHED', 'ARCHIVED');
CREATE TYPE "BlockCategory" AS ENUM ('HERO', 'CTA', 'PRICING', 'TESTIMONIALS', 'FORM', 'FAQ');
CREATE TYPE "ExperimentStatus" AS ENUM ('DRAFT', 'RUNNING', 'PAUSED', 'COMPLETED');

CREATE TABLE "User" (
  "id" TEXT PRIMARY KEY,
  "email" TEXT NOT NULL UNIQUE,
  "name" TEXT NOT NULL,
  "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "Organization" (
  "id" TEXT PRIMARY KEY,
  "name" TEXT NOT NULL,
  "slug" TEXT NOT NULL UNIQUE,
  "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "Membership" (
  "id" TEXT PRIMARY KEY,
  "userId" TEXT NOT NULL REFERENCES "User"("id") ON DELETE CASCADE,
  "organizationId" TEXT NOT NULL REFERENCES "Organization"("id") ON DELETE CASCADE,
  "role" "Role" NOT NULL DEFAULT 'MARKETER',
  "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE ("userId", "organizationId")
);

CREATE TABLE "Site" (
  "id" TEXT PRIMARY KEY,
  "organizationId" TEXT NOT NULL REFERENCES "Organization"("id") ON DELETE CASCADE,
  "name" TEXT NOT NULL,
  "slug" TEXT NOT NULL,
  "status" "SiteStatus" NOT NULL DEFAULT 'DRAFT',
  "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "publishedAt" TIMESTAMP,
  UNIQUE ("organizationId", "slug")
);

CREATE TABLE "Page" (
  "id" TEXT PRIMARY KEY,
  "siteId" TEXT NOT NULL REFERENCES "Site"("id") ON DELETE CASCADE,
  "title" TEXT NOT NULL,
  "slug" TEXT NOT NULL,
  "document" JSONB NOT NULL,
  "version" INTEGER NOT NULL DEFAULT 1,
  "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE ("siteId", "slug")
);

CREATE TABLE "BlockTemplate" (
  "id" TEXT PRIMARY KEY,
  "organizationId" TEXT NOT NULL REFERENCES "Organization"("id") ON DELETE CASCADE,
  "name" TEXT NOT NULL,
  "category" "BlockCategory" NOT NULL,
  "document" JSONB NOT NULL,
  "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "Experiment" (
  "id" TEXT PRIMARY KEY,
  "organizationId" TEXT NOT NULL REFERENCES "Organization"("id") ON DELETE CASCADE,
  "siteId" TEXT NOT NULL REFERENCES "Site"("id") ON DELETE CASCADE,
  "name" TEXT NOT NULL,
  "status" "ExperimentStatus" NOT NULL DEFAULT 'DRAFT',
  "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "ExperimentVariant" (
  "id" TEXT PRIMARY KEY,
  "experimentId" TEXT NOT NULL REFERENCES "Experiment"("id") ON DELETE CASCADE,
  "name" TEXT NOT NULL,
  "trafficShare" INTEGER NOT NULL,
  "pageDocument" JSONB NOT NULL,
  "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "ConversionEvent" (
  "id" TEXT PRIMARY KEY,
  "organizationId" TEXT NOT NULL,
  "siteId" TEXT NOT NULL REFERENCES "Site"("id") ON DELETE CASCADE,
  "pageSlug" TEXT NOT NULL,
  "eventName" TEXT NOT NULL,
  "source" TEXT,
  "metadata" JSONB,
  "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "LayoutSuggestion" (
  "id" TEXT PRIMARY KEY,
  "siteId" TEXT NOT NULL REFERENCES "Site"("id") ON DELETE CASCADE,
  "title" TEXT NOT NULL,
  "rationale" TEXT NOT NULL,
  "confidence" DOUBLE PRECISION NOT NULL,
  "proposedDocument" JSONB NOT NULL,
  "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE "AuditLog" (
  "id" TEXT PRIMARY KEY,
  "organizationId" TEXT NOT NULL REFERENCES "Organization"("id") ON DELETE CASCADE,
  "actorId" TEXT REFERENCES "User"("id") ON DELETE SET NULL,
  "action" TEXT NOT NULL,
  "targetType" TEXT NOT NULL,
  "targetId" TEXT NOT NULL,
  "metadata" JSONB,
  "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX "Membership_organizationId_role_idx" ON "Membership"("organizationId", "role");
CREATE INDEX "Site_organizationId_status_idx" ON "Site"("organizationId", "status");
CREATE INDEX "Page_siteId_idx" ON "Page"("siteId");
CREATE INDEX "BlockTemplate_organizationId_category_idx" ON "BlockTemplate"("organizationId", "category");
CREATE INDEX "Experiment_organizationId_status_idx" ON "Experiment"("organizationId", "status");
CREATE INDEX "Experiment_siteId_idx" ON "Experiment"("siteId");
CREATE INDEX "ExperimentVariant_experimentId_idx" ON "ExperimentVariant"("experimentId");
CREATE INDEX "ConversionEvent_organizationId_createdAt_idx" ON "ConversionEvent"("organizationId", "createdAt");
CREATE INDEX "ConversionEvent_siteId_createdAt_idx" ON "ConversionEvent"("siteId", "createdAt");
CREATE INDEX "ConversionEvent_eventName_idx" ON "ConversionEvent"("eventName");
CREATE INDEX "LayoutSuggestion_siteId_createdAt_idx" ON "LayoutSuggestion"("siteId", "createdAt");
CREATE INDEX "AuditLog_organizationId_createdAt_idx" ON "AuditLog"("organizationId", "createdAt");
CREATE INDEX "AuditLog_targetType_targetId_idx" ON "AuditLog"("targetType", "targetId");
