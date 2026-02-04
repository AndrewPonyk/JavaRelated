interface BackupScheduleConfig {
    enabled: boolean;
    dailySchedule: string;
    weeklySchedule: string;
    monthlySchedule: string;
    cleanupSchedule: string;
    retentionDays: number;
    maxConcurrentBackups: number;
    notificationWebhook?: string;
}
export declare class BackupScheduler {
    private config;
    private backupManager;
    private scheduledBackups;
    private runningBackups;
    constructor(config?: Partial<BackupScheduleConfig>);
    start(): void;
    stop(): void;
    triggerManualBackup(options?: {
        type?: 'manual' | 'daily' | 'weekly' | 'monthly';
        includeSchema?: boolean;
        includeData?: boolean;
        specificTables?: string[];
    }): Promise<void>;
    getScheduleStatus(): {
        enabled: boolean;
        schedules: Array<{
            id: string;
            type: string;
            schedule: string;
            lastRun?: string;
            nextRun?: string;
            status: string;
        }>;
        runningBackups: string[];
        stats: {
            totalBackups: number;
            successfulBackups: number;
            failedBackups: number;
        };
    };
    getBackupStats(): Promise<{
        totalBackups: number;
        totalSize: number;
        oldestBackup?: string;
        newestBackup?: string;
        averageSize: number;
        compressionRatio?: number;
    }>;
    verifyAllBackups(): Promise<{
        total: number;
        valid: number;
        invalid: string[];
        errors: string[];
    }>;
    private scheduleBackup;
    private scheduleCleanup;
    private executeBackup;
    private getNextRunTime;
    private logScheduleInfo;
    private sendCleanupNotification;
}
export declare const backupScheduler: BackupScheduler;
export {};
//# sourceMappingURL=backupScheduler.d.ts.map