/* =========================================================================
   V001 — Initial schema for the Invoice Factoring Platform.
   This mirrors the EF Core model (Infrastructure/Persistence). In the app,
   EF Core migrations are the source of truth; this script documents the
   schema and supports tools that apply raw SQL. Apply with the expand/contract
   pattern for zero-downtime deploys (TECH-NOTES §3.3).
   ========================================================================= */

IF OBJECT_ID(N'dbo.Companies', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Companies
    (
        Id                        UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_Companies PRIMARY KEY,
        LegalName                 NVARCHAR(200)    NOT NULL,
        TaxId                     NVARCHAR(32)     NOT NULL,   -- Always Encrypted in production
        ContactEmail              NVARCHAR(256)    NOT NULL,
        StripeConnectedAccountId  NVARCHAR(64)     NULL,
        PlaidItemId               NVARCHAR(64)     NULL,
        IsBankVerified            BIT              NOT NULL CONSTRAINT DF_Companies_BankVerified DEFAULT(0),
        CreatedAt                 DATETIMEOFFSET   NOT NULL,
        UpdatedAt                 DATETIMEOFFSET   NULL
    );
    CREATE UNIQUE INDEX UX_Companies_ContactEmail ON dbo.Companies(ContactEmail);
    CREATE UNIQUE INDEX UX_Companies_TaxId        ON dbo.Companies(TaxId);
END;
GO

IF OBJECT_ID(N'dbo.Invoices', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Invoices
    (
        Id                  UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_Invoices PRIMARY KEY,
        CompanyId           UNIQUEIDENTIFIER NOT NULL,
        DebtorName          NVARCHAR(200)    NOT NULL,
        DebtorTaxId         NVARCHAR(32)     NOT NULL,
        FaceValueAmount     DECIMAL(18,2)    NOT NULL,
        FaceValueCurrency   NVARCHAR(3)      NOT NULL,
        IssueDate           DATE             NOT NULL,
        DueDate             DATE             NOT NULL,
        Status              NVARCHAR(20)     NOT NULL,
        DocumentUri         NVARCHAR(1024)   NULL,
        CreditAssessmentId  UNIQUEIDENTIFIER NULL,
        DeclineReason       NVARCHAR(500)    NULL,
        CreatedAt           DATETIMEOFFSET   NOT NULL,
        UpdatedAt           DATETIMEOFFSET   NULL,
        CONSTRAINT FK_Invoices_Companies FOREIGN KEY (CompanyId) REFERENCES dbo.Companies(Id)
    );
    CREATE INDEX IX_Invoices_Company_Status ON dbo.Invoices(CompanyId, Status);
    CREATE INDEX IX_Invoices_Status         ON dbo.Invoices(Status);
END;
GO

IF OBJECT_ID(N'dbo.CreditAssessments', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.CreditAssessments
    (
        Id                    UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_CreditAssessments PRIMARY KEY,
        InvoiceId             UNIQUEIDENTIFIER NOT NULL,
        ProbabilityOfDefault  FLOAT            NOT NULL,
        RiskGrade             NVARCHAR(2)      NOT NULL,
        AdvanceRate           DECIMAL(5,4)     NOT NULL,
        DiscountFeeRate       DECIMAL(5,4)     NOT NULL,
        ReasonCodesJson       NVARCHAR(MAX)    NOT NULL,   -- adverse-action attributions
        ModelVersion          NVARCHAR(32)     NOT NULL,
        CreatedAt             DATETIMEOFFSET   NOT NULL,
        UpdatedAt             DATETIMEOFFSET   NULL,
        CONSTRAINT FK_CreditAssessments_Invoices FOREIGN KEY (InvoiceId)
            REFERENCES dbo.Invoices(Id) ON DELETE CASCADE
    );
    CREATE UNIQUE INDEX UX_CreditAssessments_InvoiceId ON dbo.CreditAssessments(InvoiceId);
END;
GO

IF OBJECT_ID(N'dbo.Advances', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Advances
    (
        Id                   UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_Advances PRIMARY KEY,
        InvoiceId            UNIQUEIDENTIFIER NOT NULL,
        CompanyId            UNIQUEIDENTIFIER NOT NULL,
        GrossAdvanceAmount   DECIMAL(18,2)    NOT NULL,
        GrossAdvanceCurrency NVARCHAR(3)      NOT NULL,
        FeeAmount            DECIMAL(18,2)    NOT NULL,
        FeeCurrency          NVARCHAR(3)      NOT NULL,
        NetDisbursedAmount   DECIMAL(18,2)    NOT NULL,
        NetDisbursedCurrency NVARCHAR(3)      NOT NULL,
        ReserveAmount        DECIMAL(18,2)    NOT NULL,
        ReserveCurrency      NVARCHAR(3)      NOT NULL,
        Status               NVARCHAR(20)     NOT NULL,
        StripePayoutId       NVARCHAR(64)     NULL,
        CreatedAt            DATETIMEOFFSET   NOT NULL,
        UpdatedAt            DATETIMEOFFSET   NULL,
        CONSTRAINT FK_Advances_Invoices  FOREIGN KEY (InvoiceId) REFERENCES dbo.Invoices(Id),
        CONSTRAINT FK_Advances_Companies FOREIGN KEY (CompanyId) REFERENCES dbo.Companies(Id)
    );
    CREATE UNIQUE INDEX UX_Advances_InvoiceId    ON dbo.Advances(InvoiceId);
    CREATE INDEX        IX_Advances_StripePayout ON dbo.Advances(StripePayoutId);
    CREATE INDEX        IX_Advances_Status       ON dbo.Advances(Status);
END;
GO
