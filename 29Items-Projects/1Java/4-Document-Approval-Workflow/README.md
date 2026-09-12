# Document Approval Workflow

Enterprise Event-Driven Document Approval System featuring **Java 17 / Eclipse Vert.x**, **MongoDB**, **Quartz Scheduler**, **Python spaCy NLP Microservice**, and a modern **React (TypeScript)** Web Interface.

---

## Core Application Capabilities

1. **Document Submission Modal**: Users can create and submit new approval documents with customizable titles and full body text through a dedicated modal. Input validation ensures all required fields are filled before dispatching to the backend.
2. **Creator Identity & Metadata Tagging**: Every newly submitted document is automatically tagged with the creator's unique user ID, display name, email, and submission timestamp. This metadata establishes accountability throughout the document lifecycle.
3. **NLP Sentiment & Polarity Analysis**: The Python spaCy microservice evaluates document text tone, generating a polarity rating (`POSITIVE`, `NEUTRAL`, `URGENT`, or `NEGATIVE`). This gives reviewers immediate context on tone before reading the full body.
4. **Automated Category Classification**: Incoming document text is analyzed and classified into domain categories like `LEGAL_RISK`, `FINANCIAL`, or `GENERAL`. This categorization drives downstream routing logic automatically.
5. **Dynamic Urgency Scoring**: The NLP engine computes a numerical urgency score between `0.0` and `1.0` based on language patterns, deadlines, and keywords. High urgency scores trigger priority routing and tighter review deadlines.
6. **Named Entity Recognition (NER)**: The system extracts key domain entities—such as company names, monetary values, and vendor names—directly from document text. These extracted entities appear as interactive chips on document cards.
7. **Dynamic Multi-Tier Route Generation**: Document approval chains are automatically constructed upon ingestion based on NLP categorization (e.g., routing legal risks to `LEGAL_COUNSEL` and financial agreements to `FINANCE_CONTROLLER`). Each step is assigned an explicit sequence tier.
8. **Enterprise Persona Switching**: Users can toggle between 7 distinct enterprise roles (`CREATOR`, `TEAM_LEAD`, `DEPARTMENT_HEAD`, `LEGAL_COUNSEL`, `FINANCE_CONTROLLER`, `EXECUTIVE`, and `ADMIN`) in the top navigation bar. This enables instant simulation of multi-user enterprise environments without re-logging in.
9. **Role-Based Action Authorization (RBAC)**: Review action buttons are enabled only when the user's active persona matches the specific role required for the document's current tier. Non-authorized users see a disabled state preventing out-of-turn actions.
10. **Administrator Superuser Override**: The `ADMIN` persona possesses universal override authority to approve, reject, or request revisions on any document regardless of tier role requirements. This provides administrative governance for expedited approvals.
11. **Tier Approval & Chain Progression**: Authorized reviewers can approve their assigned tier and submit review comments. The engine marks that step `APPROVED`, timestamps the action, and advances the document to the subsequent tier.
12. **Multi-Tier Final Approval**: Once the final approval tier is completed, the engine marks the overall document state as `APPROVED`. A concluding completion event is recorded in the permanent audit trail.
13. **Workflow Rejection & Halting**: A reviewer can reject a document at any tier with mandatory explanatory comments. Rejection immediately halts subsequent tiers and updates document status to `REJECTED`.
14. **Revision Request & Creator Feedback Loop**: Reviewers can send documents back for changes by selecting `REQUEST_REVISION` with required feedback. The document status transitions to `REVISION_REQUIRED` so creators can make necessary edits.
15. **Mandatory Reviewer Comment Logging**: Every decision action enforces the capture of reviewer notes and rationale. These comments are permanently bound to the specific tier step and visible across the history view.
16. **Quartz Per-Tier SLA Timer Scheduling**: The backend Quartz scheduler automatically registers a background timer whenever a document enters a review tier (e.g., 4h for Tier 1, 24h for Tier 2). Deadlines are calculated from the moment the tier becomes active.
17. **SLA Timer Cancellation & Reset**: When a reviewer acts before the deadline, Quartz cancels the active tier timer and establishes a fresh deadline window for the next tier. This prevents stale timers from triggering false alarms.
18. **Automated SLA Breach Escalation**: If an approval deadline expires, Quartz triggers an `SLA_BREACHED` event that automatically marks the document as breached. The document is instantly escalated into an executive priority queue.
19. **Live Server-Sent Events (SSE) Streaming**: The Vert.x EventBus publishes live messages over an SSE channel whenever documents are created, approved, rejected, or breached. Dashboards consume this event stream to stay updated in real time.
20. **Real-Time UI Synchronization**: Connected browser sessions automatically update lists, counts, and active details when events arrive without manual page reloads. Multiple open windows immediately reflect changes made by other reviewers.
21. **Visual Approval Pipeline Timeline**: Each document displays an interactive pipeline graphic illustrating completed, active, pending, and skipped tiers. Reviewer names, timestamps, and step statuses are visualized sequentially.
22. **Status-Based Tab Filtering**: Users can filter the document dashboard across pre-set views including `All`, `In Review`, `Approved`, `Rejected`, and `Action Required`. This allows reviewers to focus on specific operational subsets.
23. **Action-Required Task Queue**: The dashboard highlights documents specifically awaiting action from the currently selected user persona. Reviewers can immediately identify documents where their input is blocking progress.
24. **Visual Sentiment & Urgency Indicators**: Color-coded sentiment badges and urgency progress bars provide visual warnings on high-risk submissions. Reviewers can gauge document priority at a single glance.
25. **Detailed Document Inspection Pane**: Selecting any document card opens a comprehensive detail view containing the full text, metadata, creator profile, NLP insights, and SLA countdown. All controls and audit histories are consolidated in one panel.
26. **Immutable Chronological Audit Trail**: Every lifecycle event (`DOCUMENT_CREATED`, `TIER_APPROVED`, `DOCUMENT_REJECTED`, `SLA_BREACHED`) is appended to an immutable audit log. Each entry records the exact action, author, timestamp, and notes.
27. **Reactive MongoDB CRUD & Persistence**: All documents, approval steps, and audit logs are persisted via the non-blocking Vert.x MongoDB Reactive Client. Compound indexes ensure sub-millisecond retrieval under high query loads.
28. **Backend & Microservice Health Monitoring**: Dedicated `/health` endpoints on both the Vert.x Java backend and Python FastAPI NLP service report live engine and connectivity statuses. Docker container health checks continuously verify system availability.

---

## Quickstart with Docker Compose

Ensure Docker and Docker Compose are installed:

```bash
# 1. Copy environment variables
cp .env.example .env

# 2. Launch all services (MongoDB, Vert.x Backend, spaCy NLP Service, React UI)
docker-compose up --build
```

### Access URLs:
- **Frontend Dashboard**: `http://localhost:3000`
- **Vert.x REST API**: `http://localhost:8080/api/v1/documents`
- **API Health Check**: `http://localhost:8080/health`
- **spaCy NLP Service**: `http://localhost:8000/docs`
- **MongoDB**: `mongodb://localhost:27017`

---

## End-to-End Verification Results

Automated end-to-end browser and API verification results for the workflow platform running via Docker Compose:

| Feature Tested | Result | Details |
| :--- | :--- | :--- |
| **Dashboard Loading** | **Passed** | Loads immediately at `http://localhost:3000`; seeded document displayed with NLP metrics and timeline. |
| **Role-Based Access (RBAC)** | **Passed** | Switching personas in the header updates permissions and active tier action buttons. |
| **Workflow State Transition** | **Passed** | `TEAM_LEAD` approved Tier 1; document advanced to Tier 2 (`LEGAL_COUNSEL`), resetting SLA timer to 24h. |
| **Document Ingestion & spaCy NLP** | **Passed** | Submitted *"Annual IT Security Compliance Audit"*; spaCy microservice analyzed and categorized as `LEGAL_RISK` and auto-routed to `LEGAL_COUNSEL -> EXECUTIVE`. |
| **Real-time EventBus Stream** | **Passed** | SSE updates instantly updated the UI without full page refresh. |

---

## Architecture & Engineering Documentation
- [Project Plan & File Structure](file:///docs/PROJECT-PLAN.md)
- [Architecture & Data Flow](file:///docs/ARCHITECTURE.md)
- [Technical Notes & CI/CD](file:///docs/TECH-NOTES.md)

