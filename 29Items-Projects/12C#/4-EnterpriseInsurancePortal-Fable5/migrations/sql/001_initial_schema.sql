IF OBJECT_ID(N'[__EFMigrationsHistory]') IS NULL
BEGIN
    CREATE TABLE [__EFMigrationsHistory] (
        [MigrationId] nvarchar(150) NOT NULL,
        [ProductVersion] nvarchar(32) NOT NULL,
        CONSTRAINT [PK___EFMigrationsHistory] PRIMARY KEY ([MigrationId])
    );
END;
GO

BEGIN TRANSACTION;
GO

IF NOT EXISTS (
    SELECT * FROM [__EFMigrationsHistory]
    WHERE [MigrationId] = N'20260611230615_InitialCreate'
)
BEGIN
    CREATE TABLE [AuditEntries] (
        [Id] bigint NOT NULL IDENTITY,
        [Action] nvarchar(100) NOT NULL,
        [EntityName] nvarchar(100) NOT NULL,
        [EntityId] uniqueidentifier NOT NULL,
        [Actor] nvarchar(200) NOT NULL,
        [Details] nvarchar(max) NULL,
        [OccurredAtUtc] datetime2 NOT NULL,
        CONSTRAINT [PK_AuditEntries] PRIMARY KEY ([Id])
    );
END;
GO

IF NOT EXISTS (
    SELECT * FROM [__EFMigrationsHistory]
    WHERE [MigrationId] = N'20260611230615_InitialCreate'
)
BEGIN
    CREATE TABLE [Customers] (
        [Id] uniqueidentifier NOT NULL,
        [FirstName] nvarchar(100) NOT NULL,
        [LastName] nvarchar(100) NOT NULL,
        [Email] nvarchar(320) NOT NULL,
        [DateOfBirth] date NOT NULL,
        CONSTRAINT [PK_Customers] PRIMARY KEY ([Id])
    );
END;
GO

IF NOT EXISTS (
    SELECT * FROM [__EFMigrationsHistory]
    WHERE [MigrationId] = N'20260611230615_InitialCreate'
)
BEGIN
    CREATE TABLE [OutboxMessages] (
        [Id] bigint NOT NULL IDENTITY,
        [EventType] nvarchar(200) NOT NULL,
        [Key] nvarchar(100) NOT NULL,
        [Payload] nvarchar(max) NOT NULL,
        [CreatedAtUtc] datetime2 NOT NULL,
        [ProcessedAtUtc] datetime2 NULL,
        CONSTRAINT [PK_OutboxMessages] PRIMARY KEY ([Id])
    );
END;
GO

IF NOT EXISTS (
    SELECT * FROM [__EFMigrationsHistory]
    WHERE [MigrationId] = N'20260611230615_InitialCreate'
)
BEGIN
    CREATE TABLE [Quotes] (
        [Id] uniqueidentifier NOT NULL,
        [CustomerId] uniqueidentifier NOT NULL,
        [BrokerId] nvarchar(50) NOT NULL,
        [ProductCode] nvarchar(30) NOT NULL,
        [StateCode] nvarchar(2) NOT NULL,
        [RiskFactorsJson] nvarchar(max) NOT NULL,
        [Premium] decimal(18,2) NOT NULL,
        [RateTableVersion] nvarchar(50) NOT NULL,
        [Status] nvarchar(20) NOT NULL,
        [IssuedAtUtc] datetime2 NOT NULL,
        [ExpiresAtUtc] datetime2 NOT NULL,
        CONSTRAINT [PK_Quotes] PRIMARY KEY ([Id]),
        CONSTRAINT [FK_Quotes_Customers_CustomerId] FOREIGN KEY ([CustomerId]) REFERENCES [Customers] ([Id]) ON DELETE CASCADE
    );
END;
GO

IF NOT EXISTS (
    SELECT * FROM [__EFMigrationsHistory]
    WHERE [MigrationId] = N'20260611230615_InitialCreate'
)
BEGIN
    CREATE TABLE [Policies] (
        [Id] uniqueidentifier NOT NULL,
        [PolicyNumber] nvarchar(30) NOT NULL,
        [QuoteId] uniqueidentifier NOT NULL,
        [CustomerId] uniqueidentifier NOT NULL,
        [AnnualPremium] decimal(18,2) NOT NULL,
        [EffectiveDate] date NOT NULL,
        [ExpiryDate] date NOT NULL,
        [Status] nvarchar(20) NOT NULL,
        [CancellationReason] nvarchar(500) NULL,
        [CancelledAtUtc] datetime2 NULL,
        CONSTRAINT [PK_Policies] PRIMARY KEY ([Id]),
        CONSTRAINT [FK_Policies_Customers_CustomerId] FOREIGN KEY ([CustomerId]) REFERENCES [Customers] ([Id]) ON DELETE NO ACTION,
        CONSTRAINT [FK_Policies_Quotes_QuoteId] FOREIGN KEY ([QuoteId]) REFERENCES [Quotes] ([Id]) ON DELETE NO ACTION
    );
END;
GO

IF NOT EXISTS (
    SELECT * FROM [__EFMigrationsHistory]
    WHERE [MigrationId] = N'20260611230615_InitialCreate'
)
BEGIN
    CREATE TABLE [Claims] (
        [Id] uniqueidentifier NOT NULL,
        [PolicyId] uniqueidentifier NOT NULL,
        [Description] nvarchar(2000) NOT NULL,
        [ClaimedAmount] decimal(18,2) NOT NULL,
        [ApprovedAmount] decimal(18,2) NULL,
        [Status] nvarchar(20) NOT NULL,
        [FiledAtUtc] datetime2 NOT NULL,
        [ResolvedAtUtc] datetime2 NULL,
        CONSTRAINT [PK_Claims] PRIMARY KEY ([Id]),
        CONSTRAINT [FK_Claims_Policies_PolicyId] FOREIGN KEY ([PolicyId]) REFERENCES [Policies] ([Id]) ON DELETE CASCADE
    );
END;
GO

IF NOT EXISTS (
    SELECT * FROM [__EFMigrationsHistory]
    WHERE [MigrationId] = N'20260611230615_InitialCreate'
)
BEGIN
    CREATE INDEX [IX_AuditEntries_EntityName_EntityId] ON [AuditEntries] ([EntityName], [EntityId]);
END;
GO

IF NOT EXISTS (
    SELECT * FROM [__EFMigrationsHistory]
    WHERE [MigrationId] = N'20260611230615_InitialCreate'
)
BEGIN
    CREATE INDEX [IX_AuditEntries_OccurredAtUtc] ON [AuditEntries] ([OccurredAtUtc]);
END;
GO

IF NOT EXISTS (
    SELECT * FROM [__EFMigrationsHistory]
    WHERE [MigrationId] = N'20260611230615_InitialCreate'
)
BEGIN
    CREATE INDEX [IX_Claims_PolicyId] ON [Claims] ([PolicyId]);
END;
GO

IF NOT EXISTS (
    SELECT * FROM [__EFMigrationsHistory]
    WHERE [MigrationId] = N'20260611230615_InitialCreate'
)
BEGIN
    CREATE UNIQUE INDEX [IX_Customers_Email] ON [Customers] ([Email]);
END;
GO

IF NOT EXISTS (
    SELECT * FROM [__EFMigrationsHistory]
    WHERE [MigrationId] = N'20260611230615_InitialCreate'
)
BEGIN
    EXEC(N'CREATE INDEX [IX_OutboxMessages_ProcessedAtUtc] ON [OutboxMessages] ([ProcessedAtUtc]) WHERE [ProcessedAtUtc] IS NULL');
END;
GO

IF NOT EXISTS (
    SELECT * FROM [__EFMigrationsHistory]
    WHERE [MigrationId] = N'20260611230615_InitialCreate'
)
BEGIN
    CREATE INDEX [IX_Policies_CustomerId] ON [Policies] ([CustomerId]);
END;
GO

IF NOT EXISTS (
    SELECT * FROM [__EFMigrationsHistory]
    WHERE [MigrationId] = N'20260611230615_InitialCreate'
)
BEGIN
    CREATE UNIQUE INDEX [IX_Policies_PolicyNumber] ON [Policies] ([PolicyNumber]);
END;
GO

IF NOT EXISTS (
    SELECT * FROM [__EFMigrationsHistory]
    WHERE [MigrationId] = N'20260611230615_InitialCreate'
)
BEGIN
    CREATE UNIQUE INDEX [IX_Policies_QuoteId] ON [Policies] ([QuoteId]);
END;
GO

IF NOT EXISTS (
    SELECT * FROM [__EFMigrationsHistory]
    WHERE [MigrationId] = N'20260611230615_InitialCreate'
)
BEGIN
    CREATE INDEX [IX_Quotes_BrokerId_Status] ON [Quotes] ([BrokerId], [Status]);
END;
GO

IF NOT EXISTS (
    SELECT * FROM [__EFMigrationsHistory]
    WHERE [MigrationId] = N'20260611230615_InitialCreate'
)
BEGIN
    CREATE INDEX [IX_Quotes_CustomerId] ON [Quotes] ([CustomerId]);
END;
GO

IF NOT EXISTS (
    SELECT * FROM [__EFMigrationsHistory]
    WHERE [MigrationId] = N'20260611230615_InitialCreate'
)
BEGIN
    INSERT INTO [__EFMigrationsHistory] ([MigrationId], [ProductVersion])
    VALUES (N'20260611230615_InitialCreate', N'8.0.12');
END;
GO

COMMIT;
GO

