#!/usr/bin/env tsx
interface BackupConfig {
    databaseUrl: string;
    backupDir: string;
    retentionDays: number;
    compression: boolean;
    encryptionKey?: string;
    remoteBucket?: string;
    slackWebhook?: string;
    maxBackupSize: number;
    backupTimeout: number;
}
interface BackupMetadata {
    filename: string;
    timestamp: string;
    size: number;
    compressed: boolean;
    encrypted: boolean;
    checksum: string;
    databaseSize: number;
    tables: string[];
    version: string;
    duration: number;
}
export declare class DatabaseBackupManager {
    private config;
    constructor(config?: Partial<BackupConfig>);
    createBackup(options?: {
        includeSchema?: boolean;
        includeData?: boolean;
        specificTables?: string[];
        customSuffix?: string;
    }): Promise<BackupMetadata>;
    restoreBackup(backupFilename: string, options?: {
        dropExisting?: boolean;
        createDatabase?: boolean;
        targetDatabase?: string;
        dryRun?: boolean;
    }): Promise<void>;
    listBackups(): Promise<BackupMetadata[]>;
    cleanupOldBackups(): Promise<{
        deleted: string[];
        errors: string[];
    }>;
    verifyBackup(backupFilename: string): Promise<{
        valid: boolean;
        errors: string[];
    }>;
    private buildPgDumpCommand;
    private buildRestoreCommand;
    private executeWithTimeout;
    private compressFile;
    private calculateChecksum;
    private validateDatabaseConnection;
    private checkDiskSpace;
    private getDatabaseInfo;
    private getMetadataPath;
    private saveMetadata;
    private loadMetadata;
    private validateBackupIntegrity;
    private prepareRestoreFile;
    private testDecompression;
    private uploadToRemoteStorage;
    private sendNotification;
}
export default DatabaseBackupManager;
//# sourceMappingURL=database-backup.d.ts.map