import cron from 'node-cron';
import { DatabaseBackupManager } from './database-backup.js';
import { logger } from '@/utils/logger.js';
import { config } from '@/config/environment.js';

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

interface ScheduledBackup {
  id: string;
  type: 'daily' | 'weekly' | 'monthly';
  schedule: string;
  lastRun?: Date;
  nextRun?: Date;
  status: 'pending' | 'running' | 'completed' | 'failed';
  task?: cron.ScheduledTask;
}

export class BackupScheduler {
  private config: BackupScheduleConfig;
  private backupManager: DatabaseBackupManager;
  private scheduledBackups: Map<string, ScheduledBackup> = new Map();
  private runningBackups: Set<string> = new Set();

  constructor(config?: Partial<BackupScheduleConfig>) {
    this.config = {
      enabled: process.env.BACKUP_ENABLED === 'true' || config?.enabled || false,
      dailySchedule: process.env.BACKUP_DAILY_SCHEDULE || config?.dailySchedule || '0 2 * * *', // 2 AM daily
      weeklySchedule: process.env.BACKUP_WEEKLY_SCHEDULE || config?.weeklySchedule || '0 3 * * 0', // 3 AM Sunday
      monthlySchedule: process.env.BACKUP_MONTHLY_SCHEDULE || config?.monthlySchedule || '0 4 1 * *', // 4 AM 1st of month
      cleanupSchedule: process.env.BACKUP_CLEANUP_SCHEDULE || config?.cleanupSchedule || '0 5 * * 0', // 5 AM Sunday
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

  /**
   * Start the backup scheduler
   */
  public start(): void {
    if (!this.config.enabled) {
      logger.info('Backup scheduler is disabled');
      return;
    }

    logger.info('Starting backup scheduler...');

    // Schedule daily backups
    this.scheduleBackup('daily', this.config.dailySchedule, async () => {
      await this.executeBackup('daily', { customSuffix: 'daily' });
    });

    // Schedule weekly backups
    this.scheduleBackup('weekly', this.config.weeklySchedule, async () => {
      await this.executeBackup('weekly', { customSuffix: 'weekly' });
    });

    // Schedule monthly backups
    this.scheduleBackup('monthly', this.config.monthlySchedule, async () => {
      await this.executeBackup('monthly', { customSuffix: 'monthly' });
    });

    // Schedule cleanup
    this.scheduleCleanup();

    // Log next run times
    this.logScheduleInfo();

    logger.info('Backup scheduler started successfully');
  }

  /**
   * Stop the backup scheduler
   */
  public stop(): void {
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

  /**
   * Trigger manual backup
   */
  public async triggerManualBackup(options: {
    type?: 'manual' | 'daily' | 'weekly' | 'monthly';
    includeSchema?: boolean;
    includeData?: boolean;
    specificTables?: string[];
  } = {}): Promise<void> {
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
    } catch (error) {
      logger.error('Manual backup failed', { type: backupType, error });
      throw error;
    }
  }

  /**
   * Get backup schedule status
   */
  public getScheduleStatus(): {
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
  } {
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
        totalBackups: 0, // Would be populated from backup history
        successfulBackups: 0,
        failedBackups: 0,
      },
    };
  }

  /**
   * Get backup history and statistics
   */
  public async getBackupStats(): Promise<{
    totalBackups: number;
    totalSize: number;
    oldestBackup?: string;
    newestBackup?: string;
    averageSize: number;
    compressionRatio?: number;
  }> {
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

      // Sort by timestamp
      const sortedBackups = backups.sort((a, b) =>
        new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
      );

      const oldestBackup = sortedBackups[0]?.timestamp;
      const newestBackup = sortedBackups[sortedBackups.length - 1]?.timestamp;

      return {
        totalBackups: backups.length,
        totalSize,
        oldestBackup,
        newestBackup,
        averageSize,
      };
    } catch (error) {
      logger.error('Failed to get backup statistics', { error });
      return {
        totalBackups: 0,
        totalSize: 0,
        averageSize: 0,
      };
    }
  }

  /**
   * Verify all backups
   */
  public async verifyAllBackups(): Promise<{
    total: number;
    valid: number;
    invalid: string[];
    errors: string[];
  }> {
    logger.info('Starting verification of all backups');

    const backups = await this.backupManager.listBackups();
    const invalid: string[] = [];
    const errors: string[] = [];
    let valid = 0;

    for (const backup of backups) {
      try {
        const result = await this.backupManager.verifyBackup(backup.filename);

        if (result.valid) {
          valid++;
          logger.debug('Backup verification passed', { filename: backup.filename });
        } else {
          invalid.push(backup.filename);
          errors.push(...result.errors);
          logger.warn('Backup verification failed', {
            filename: backup.filename,
            errors: result.errors
          });
        }
      } catch (error) {
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

  // Private methods
  private scheduleBackup(
    type: 'daily' | 'weekly' | 'monthly',
    schedule: string,
    backupFunction: () => Promise<void>
  ): void {
    if (!cron.validate(schedule)) {
      logger.error('Invalid cron schedule', { type, schedule });
      return;
    }

    const id = `backup-${type}`;
    const task = cron.schedule(schedule, async () => {
      await backupFunction();
    }, {
      scheduled: false, // Don't start immediately
      name: id,
    });

    const scheduledBackup: ScheduledBackup = {
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

  private scheduleCleanup(): void {
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

        // Send notification if configured
        if (this.config.notificationWebhook && result.deleted.length > 0) {
          await this.sendCleanupNotification(result);
        }
      } catch (error) {
        logger.error('Scheduled backup cleanup failed', { error });
      }
    }, {
      name: 'backup-cleanup',
    });

    logger.info('Backup cleanup scheduled', { schedule });
  }

  private async executeBackup(type: string, options: any): Promise<void> {
    const backupId = `${type}-${Date.now()}`;

    // Check if we're already running too many backups
    if (this.runningBackups.size >= this.config.maxConcurrentBackups) {
      logger.warn('Max concurrent backups reached, skipping backup', {
        type,
        maxConcurrent: this.config.maxConcurrentBackups,
        running: this.runningBackups.size,
      });
      return;
    }

    // Update status
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

      // Update status
      if (scheduledBackup) {
        scheduledBackup.status = 'completed';
      }

    } catch (error) {
      logger.error('Scheduled backup failed', {
        type,
        backupId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      // Update status
      if (scheduledBackup) {
        scheduledBackup.status = 'failed';
      }

      throw error;

    } finally {
      this.runningBackups.delete(backupId);
    }
  }

  private getNextRunTime(cronSchedule: string): Date {
    // Simple implementation - in production, use a proper cron parser
    const now = new Date();
    const tomorrow = new Date(now);
    tomorrow.setDate(tomorrow.getDate() + 1);
    return tomorrow;
  }

  private logScheduleInfo(): void {
    for (const [id, backup] of this.scheduledBackups) {
      logger.info('Scheduled backup registered', {
        id,
        type: backup.type,
        schedule: backup.schedule,
        nextRun: backup.nextRun?.toISOString(),
      });
    }
  }

  private async sendCleanupNotification(result: { deleted: string[]; errors: string[] }): Promise<void> {
    const message = `🧹 Backup cleanup completed\n` +
      `Deleted: ${result.deleted.length} old backups\n` +
      `Errors: ${result.errors.length}`;

    try {
      // In production, implement actual webhook call
      logger.info('Would send cleanup notification', { message });
    } catch (error) {
      logger.error('Failed to send cleanup notification', { error });
    }
  }
}

// Create global scheduler instance
export const backupScheduler = new BackupScheduler();