import cron from 'node-cron';
import { DatabaseBackupManager } from '../../../scripts/backup/database-backup.js';
import { logger } from '@/utils/logger.js';
export class BackupScheduler {
    config;
    backupManager;
    scheduledBackups = new Map();
    runningBackups = new Set();
    constructor(config) {
        this.config = {
            enabled: process.env.BACKUP_ENABLED === 'true' || config?.enabled || false,
            dailySchedule: process.env.BACKUP_DAILY_SCHEDULE || config?.dailySchedule || '0 2 * * *',
            weeklySchedule: process.env.BACKUP_WEEKLY_SCHEDULE || config?.weeklySchedule || '0 3 * * 0',
            monthlySchedule: process.env.BACKUP_MONTHLY_SCHEDULE || config?.monthlySchedule || '0 4 1 * *',
            cleanupSchedule: process.env.BACKUP_CLEANUP_SCHEDULE || config?.cleanupSchedule || '0 5 * * 0',
            retentionDays: parseInt(process.env.BACKUP_RETENTION_DAYS || '30') || config?.retentionDays || 30,
            maxConcurrentBackups: config?.maxConcurrentBackups || 1,
            notificationWebhook: process.env.BACKUP_SLACK_WEBHOOK || config?.notificationWebhook,
        };
        this.backupManager = new DatabaseBackupManager({
            backupDir: process.env.BACKUP_DIR || './backups',
            retentionDays: this.config.retentionDays,
            slackWebhook: this.config.notificationWebhook,
        });
        logger.info('Backup scheduler initialized', {
            enabled: this.config.enabled,
            dailySchedule: this.config.dailySchedule,
            weeklySchedule: this.config.weeklySchedule,
            monthlySchedule: this.config.monthlySchedule,
            retentionDays: this.config.retentionDays,
        });
    }
    start() {
        if (!this.config.enabled) {
            logger.info('Backup scheduler is disabled');
            return;
        }
        logger.info('Starting backup scheduler...');
        this.scheduleBackup('daily', this.config.dailySchedule, async () => {
            await this.executeBackup('daily', { customSuffix: 'daily' });
        });
        this.scheduleBackup('weekly', this.config.weeklySchedule, async () => {
            await this.executeBackup('weekly', { customSuffix: 'weekly' });
        });
        this.scheduleBackup('monthly', this.config.monthlySchedule, async () => {
            await this.executeBackup('monthly', { customSuffix: 'monthly' });
        });
        this.scheduleCleanup();
        this.logScheduleInfo();
        logger.info('Backup scheduler started successfully');
    }
    stop() {
        logger.info('Stopping backup scheduler...');
        for (const [id, backup] of this.scheduledBackups) {
            if (backup.task) {
                backup.task.stop();
                logger.info(`Stopped scheduled backup: ${id}`);
            }
        }
        this.scheduledBackups.clear();
        logger.info('Backup scheduler stopped');
    }
    async triggerManualBackup(options = {}) {
        const backupType = options.type || 'manual';
        logger.info('Manual backup triggered', { type: backupType, options });
        try {
            await this.executeBackup(backupType, {
                customSuffix: backupType,
                includeSchema: options.includeSchema,
                includeData: options.includeData,
                specificTables: options.specificTables,
            });
            logger.info('Manual backup completed successfully', { type: backupType });
        }
        catch (error) {
            logger.error('Manual backup failed', { type: backupType, error });
            throw error;
        }
    }
    getScheduleStatus() {
        const schedules = Array.from(this.scheduledBackups.values()).map(backup => ({
            id: backup.id,
            type: backup.type,
            schedule: backup.schedule,
            lastRun: backup.lastRun?.toISOString(),
            nextRun: backup.nextRun?.toISOString(),
            status: backup.status,
        }));
        return {
            enabled: this.config.enabled,
            schedules,
            runningBackups: Array.from(this.runningBackups),
            stats: {
                totalBackups: 0,
                successfulBackups: 0,
                failedBackups: 0,
            },
        };
    }
    async getBackupStats() {
        try {
            const backups = await this.backupManager.listBackups();
            if (backups.length === 0) {
                return {
                    totalBackups: 0,
                    totalSize: 0,
                    averageSize: 0,
                };
            }
            const totalSize = backups.reduce((sum, backup) => sum + backup.size, 0);
            const averageSize = totalSize / backups.length;
            const sortedBackups = backups.sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime());
            const oldestBackup = sortedBackups[0]?.timestamp;
            const newestBackup = sortedBackups[sortedBackups.length - 1]?.timestamp;
            return {
                totalBackups: backups.length,
                totalSize,
                oldestBackup,
                newestBackup,
                averageSize,
            };
        }
        catch (error) {
            logger.error('Failed to get backup statistics', { error });
            return {
                totalBackups: 0,
                totalSize: 0,
                averageSize: 0,
            };
        }
    }
    async verifyAllBackups() {
        logger.info('Starting verification of all backups');
        const backups = await this.backupManager.listBackups();
        const invalid = [];
        const errors = [];
        let valid = 0;
        for (const backup of backups) {
            try {
                const result = await this.backupManager.verifyBackup(backup.filename);
                if (result.valid) {
                    valid++;
                    logger.debug('Backup verification passed', { filename: backup.filename });
                }
                else {
                    invalid.push(backup.filename);
                    errors.push(...result.errors);
                    logger.warn('Backup verification failed', {
                        filename: backup.filename,
                        errors: result.errors
                    });
                }
            }
            catch (error) {
                invalid.push(backup.filename);
                const errorMessage = error instanceof Error ? error.message : 'Unknown error';
                errors.push(`${backup.filename}: ${errorMessage}`);
                logger.error('Backup verification error', {
                    filename: backup.filename,
                    error: errorMessage
                });
            }
        }
        logger.info('Backup verification completed', {
            total: backups.length,
            valid,
            invalid: invalid.length,
        });
        return {
            total: backups.length,
            valid,
            invalid,
            errors,
        };
    }
    scheduleBackup(type, schedule, backupFunction) {
        if (!cron.validate(schedule)) {
            logger.error('Invalid cron schedule', { type, schedule });
            return;
        }
        const id = `backup-${type}`;
        const task = cron.schedule(schedule, async () => {
            await backupFunction();
        }, {
            scheduled: false,
            name: id,
        });
        const scheduledBackup = {
            id,
            type,
            schedule,
            status: 'pending',
            task,
            nextRun: this.getNextRunTime(schedule),
        };
        this.scheduledBackups.set(id, scheduledBackup);
        task.start();
        logger.info('Backup scheduled', {
            type,
            schedule,
            nextRun: scheduledBackup.nextRun?.toISOString(),
        });
    }
    scheduleCleanup() {
        const schedule = this.config.cleanupSchedule;
        if (!cron.validate(schedule)) {
            logger.error('Invalid cleanup schedule', { schedule });
            return;
        }
        cron.schedule(schedule, async () => {
            logger.info('Starting scheduled backup cleanup');
            try {
                const result = await this.backupManager.cleanupOldBackups();
                logger.info('Scheduled backup cleanup completed', {
                    deleted: result.deleted.length,
                    errors: result.errors.length,
                });
                if (this.config.notificationWebhook && result.deleted.length > 0) {
                    await this.sendCleanupNotification(result);
                }
            }
            catch (error) {
                logger.error('Scheduled backup cleanup failed', { error });
            }
        }, {
            name: 'backup-cleanup',
        });
        logger.info('Backup cleanup scheduled', { schedule });
    }
    async executeBackup(type, options) {
        const backupId = `${type}-${Date.now()}`;
        if (this.runningBackups.size >= this.config.maxConcurrentBackups) {
            logger.warn('Max concurrent backups reached, skipping backup', {
                type,
                maxConcurrent: this.config.maxConcurrentBackups,
                running: this.runningBackups.size,
            });
            return;
        }
        const scheduledBackup = this.scheduledBackups.get(`backup-${type}`);
        if (scheduledBackup) {
            scheduledBackup.status = 'running';
            scheduledBackup.lastRun = new Date();
            scheduledBackup.nextRun = this.getNextRunTime(scheduledBackup.schedule);
        }
        this.runningBackups.add(backupId);
        try {
            logger.info('Starting scheduled backup', { type, backupId });
            const metadata = await this.backupManager.createBackup(options);
            logger.info('Scheduled backup completed successfully', {
                type,
                backupId,
                filename: metadata.filename,
                size: metadata.size,
                duration: metadata.duration,
            });
            if (scheduledBackup) {
                scheduledBackup.status = 'completed';
            }
        }
        catch (error) {
            logger.error('Scheduled backup failed', {
                type,
                backupId,
                error: error instanceof Error ? error.message : 'Unknown error',
            });
            if (scheduledBackup) {
                scheduledBackup.status = 'failed';
            }
            throw error;
        }
        finally {
            this.runningBackups.delete(backupId);
        }
    }
    getNextRunTime(cronSchedule) {
        const now = new Date();
        const tomorrow = new Date(now);
        tomorrow.setDate(tomorrow.getDate() + 1);
        return tomorrow;
    }
    logScheduleInfo() {
        for (const [id, backup] of this.scheduledBackups) {
            logger.info('Scheduled backup registered', {
                id,
                type: backup.type,
                schedule: backup.schedule,
                nextRun: backup.nextRun?.toISOString(),
            });
        }
    }
    async sendCleanupNotification(result) {
        const message = `🧹 Backup cleanup completed\n` +
            `Deleted: ${result.deleted.length} old backups\n` +
            `Errors: ${result.errors.length}`;
        try {
            logger.info('Would send cleanup notification', { message });
        }
        catch (error) {
            logger.error('Failed to send cleanup notification', { error });
        }
    }
}
export const backupScheduler = new BackupScheduler();
//# sourceMappingURL=backupScheduler.js.map