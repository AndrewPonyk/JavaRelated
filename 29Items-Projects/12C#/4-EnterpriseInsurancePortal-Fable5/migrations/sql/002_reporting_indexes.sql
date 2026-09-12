BEGIN TRANSACTION;
GO

IF NOT EXISTS (
    SELECT * FROM [__EFMigrationsHistory]
    WHERE [MigrationId] = N'20260612061430_AddReportingIndexes'
)
BEGIN
    CREATE INDEX [IX_Policies_EffectiveDate] ON [Policies] ([EffectiveDate]);
END;
GO

IF NOT EXISTS (
    SELECT * FROM [__EFMigrationsHistory]
    WHERE [MigrationId] = N'20260612061430_AddReportingIndexes'
)
BEGIN
    CREATE INDEX [IX_Policies_Status] ON [Policies] ([Status]);
END;
GO

IF NOT EXISTS (
    SELECT * FROM [__EFMigrationsHistory]
    WHERE [MigrationId] = N'20260612061430_AddReportingIndexes'
)
BEGIN
    CREATE INDEX [IX_Claims_FiledAtUtc] ON [Claims] ([FiledAtUtc]);
END;
GO

IF NOT EXISTS (
    SELECT * FROM [__EFMigrationsHistory]
    WHERE [MigrationId] = N'20260612061430_AddReportingIndexes'
)
BEGIN
    CREATE INDEX [IX_AuditEntries_Action_OccurredAtUtc] ON [AuditEntries] ([Action], [OccurredAtUtc]);
END;
GO

IF NOT EXISTS (
    SELECT * FROM [__EFMigrationsHistory]
    WHERE [MigrationId] = N'20260612061430_AddReportingIndexes'
)
BEGIN
    INSERT INTO [__EFMigrationsHistory] ([MigrationId], [ProductVersion])
    VALUES (N'20260612061430_AddReportingIndexes', N'8.0.12');
END;
GO

COMMIT;
GO

