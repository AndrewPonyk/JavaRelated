/**
 * MongoDB Initialization & Schema Validation Script
 * Database: document_workflow
 */

const dbName = 'document_workflow';
const dbInstance = db.getSiblingDB(dbName);

// 1. Create Documents Collection with JSON Schema Validation
dbInstance.createCollection('documents');

// 2. Compound Indexes for High-Performance Querying & Routing
dbInstance.documents.createIndex({ status: 1, currentTier: 1 }, { name: 'idx_status_tier' });
dbInstance.documents.createIndex({ "approvalSteps.requiredRole": 1, status: 1 }, { name: 'idx_role_status' });
dbInstance.documents.createIndex({ "sla.deadline": 1, "sla.breached": 1 }, { name: 'idx_sla_deadline' });
dbInstance.documents.createIndex({ "creator.userId": 1, createdAt: -1 }, { name: 'idx_creator_created' });
dbInstance.documents.createIndex({ "nlpAnalysis.category": 1, "nlpAnalysis.urgencyScore": -1 }, { name: 'idx_nlp_category_urgency' });

// 3. Seed Initial Demo Document
dbInstance.documents.insertOne({
  _id: "doc-seed-001",
  id: "doc-seed-001",
  title: "Q3 Vendor Procurement Contract - Cloud Migration",
  content: "Urgent vendor agreement for enterprise cloud infrastructure migration. Contract value exceeds $250k with critical data compliance clauses.",
  status: "IN_REVIEW",
  creator: {
    userId: "user-101",
    email: "creator@enterprise.io",
    name: "Alex Creator",
    role: "CREATOR"
  },
  currentTier: 1,
  approvalSteps: [
    {
      stepId: "step-1",
      tier: 1,
      requiredRole: "TEAM_LEAD",
      assignedTo: "lead-201",
      status: "PENDING",
      actionTakenAt: null,
      comments: null
    },
    {
      stepId: "step-2",
      tier: 2,
      requiredRole: "LEGAL_COUNSEL",
      assignedTo: null,
      status: "PENDING",
      actionTakenAt: null,
      comments: null
    },
    {
      stepId: "step-3",
      tier: 3,
      requiredRole: "FINANCE_CONTROLLER",
      assignedTo: null,
      status: "PENDING",
      actionTakenAt: null,
      comments: null
    }
  ],
  sla: {
    deadline: new Date(Date.now() + 4 * 3600 * 1000).toISOString(),
    breached: false,
    escalatedTo: null,
    warningSent: false
  },
  nlpAnalysis: {
    sentimentScore: -0.15,
    polarity: "NEUTRAL_URGENT",
    category: "LEGAL_RISK",
    urgencyScore: 0.85,
    extractedEntities: ["Cloud Migration", "Vendor Procurement", "$250k"]
  },
  auditTrail: [
    {
      action: "DOCUMENT_CREATED",
      performedBy: "Alex Creator",
      timestamp: new Date().toISOString(),
      details: "Initial submission with spaCy NLP classification: LEGAL_RISK (Urgency: 0.85)"
    }
  ],
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString()
});

print("MongoDB Document Approval Workflow collections and indexes initialized successfully.");
