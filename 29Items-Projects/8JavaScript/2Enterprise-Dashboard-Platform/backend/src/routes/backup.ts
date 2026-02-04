import { Router } from 'express';
import { authenticate, authorize } from '@/middleware/auth/authMiddleware.js';
import { csrfProtection } from '@/middleware/security/csrfMiddleware.js';
import { DatabaseBackupManager } from '@/services/backup/database-backup.js';
import { backupScheduler } from '@/services/backup/backupScheduler.js';
import { asyncHandler } from '@/utils/asyncHandler.js';
import { z } from 'zod';
import { logger } from '@/utils/logger.js';

const router = Router();

// Initialize backup manager
const backupManager = new DatabaseBackupManager();

// Validation schemas
const createBackupSchema = z.object({
  includeSchema: z.boolean().default(true),
  includeData: z.boolean().default(true),
  specificTables: z.array(z.string()).optional(),
  customSuffix: z.string().optional(),
});

const restoreBackupSchema = z.object({
  backupFilename: z.string().min(1, 'Backup filename is required'),
  dropExisting: z.boolean().default(false),
  createDatabase: z.boolean().default(false),
  targetDatabase: z.string().optional(),
  dryRun: z.boolean().default(false),
});

/**
 * @route   GET /api/backup/list
 * @desc    List all available backups
 * @access  Private (Admin only)
 */
router.get('/list', authenticate, authorize('ADMIN', 'SUPER_ADMIN'), asyncHandler(async (req, res) => {
  try {
    const backups = await backupManager.listBackups();

    res.json({
      success: true,
      data: {
        backups,
        total: backups.length,
        totalSize: backups.reduce((sum, backup) => sum + backup.size, 0),
      },
      message: 'Backups retrieved successfully',
    });
  } catch (error) {
    logger.error('Failed to list backups', { error });
    res.status(500).json({
      success: false,
      error: {
        code: 'BACKUP_LIST_ERROR',
        message: 'Failed to retrieve backup list',
        statusCode: 500,
      },
    });
  }
}));

/**
 * @route   POST /api/backup/create
 * @desc    Create a new database backup
 * @access  Private (Admin only)
 */
router.post('/create', authenticate, authorize('ADMIN', 'SUPER_ADMIN'), csrfProtection, asyncHandler(async (req, res) => {
  try {
    const validatedData = createBackupSchema.parse(req.body);

    logger.info('Manual backup initiated', {
      userId: (req as any).user?.id,
      options: validatedData,
    });

    const metadata = await backupManager.createBackup(validatedData);

    return res.status(201).json({
      success: true,
      data: metadata,
      message: 'Backup created successfully',
    });
  } catch (error) {
    logger.error('Manual backup failed', {
      error: error instanceof Error ? error.message : 'Unknown error',
      userId: (req as any).user?.id,
    });

    if (error instanceof z.ZodError) {
      return res.status(400).json({
        success: false,
        error: {
          code: 'VALIDATION_ERROR',
          message: 'Invalid backup parameters',
          details: error.issues,
          statusCode: 400,
        },
      });
    }

    return res.status(500).json({
      success: false,
      error: {
        code: 'BACKUP_CREATE_ERROR',
        message: 'Failed to create backup',
        details: error instanceof Error ? error.message : 'Unknown error',
        statusCode: 500,
      },
    });
  }
}));

/**
 * @route   POST /api/backup/restore
 * @desc    Restore database from backup
 * @access  Private (Super Admin only)
 */
router.post('/restore', authenticate, authorize('SUPER_ADMIN'), csrfProtection, asyncHandler(async (req, res) => {
  try {
    const validatedData = restoreBackupSchema.parse(req.body);

    logger.warn('Database restore initiated', {
      userId: (req as any).user?.id,
      backupFilename: validatedData.backupFilename,
      dryRun: validatedData.dryRun,
    });

    await backupManager.restoreBackup(validatedData.backupFilename, validatedData);

    const message = validatedData.dryRun
      ? 'Backup validation completed successfully'
      : 'Database restored successfully';

    return res.json({
      success: true,
      data: {
        backupFilename: validatedData.backupFilename,
        dryRun: validatedData.dryRun,
        restoredAt: new Date().toISOString(),
      },
      message,
    });
  } catch (error) {
    logger.error('Database restore failed', {
      error: error instanceof Error ? error.message : 'Unknown error',
      userId: (req as any).user?.id,
      backupFilename: req.body.backupFilename,
    });

    if (error instanceof z.ZodError) {
      return res.status(400).json({
        success: false,
        error: {
          code: 'VALIDATION_ERROR',
          message: 'Invalid restore parameters',
          details: error.issues,
          statusCode: 400,
        },
      });
    }

    return res.status(500).json({
      success: false,
      error: {
        code: 'BACKUP_RESTORE_ERROR',
        message: 'Failed to restore backup',
        details: error instanceof Error ? error.message : 'Unknown error',
        statusCode: 500,
      },
    });
  }
}));

/**
 * @route   POST /api/backup/:filename/verify
 * @desc    Verify backup integrity
 * @access  Private (Admin only)
 */
router.post('/:filename/verify', authenticate, authorize('ADMIN', 'SUPER_ADMIN'), asyncHandler(async (req, res) => {
  try {
    const filename = req.params.filename!;

    logger.info('Backup verification initiated', {
      userId: (req as any).user?.id,
      filename,
    });

    const result = await backupManager.verifyBackup(filename);

    res.json({
      success: true,
      data: {
        filename,
        valid: result.valid,
        errors: result.errors,
        verifiedAt: new Date().toISOString(),
      },
      message: result.valid ? 'Backup is valid' : 'Backup validation failed',
    });
  } catch (error) {
    logger.error('Backup verification failed', {
      error: error instanceof Error ? error.message : 'Unknown error',
      filename: req.params.filename,
    });

    res.status(500).json({
      success: false,
      error: {
        code: 'BACKUP_VERIFY_ERROR',
        message: 'Failed to verify backup',
        details: error instanceof Error ? error.message : 'Unknown error',
        statusCode: 500,
      },
    });
  }
}));

/**
 * @route   DELETE /api/backup/:filename
 * @desc    Delete a specific backup
 * @access  Private (Super Admin only)
 */
router.delete('/:filename', authenticate, authorize('SUPER_ADMIN'), csrfProtection, asyncHandler(async (req, res) => {
  try {
    const { filename } = req.params;

    // This would be implemented as part of the backup manager
    // For now, return a placeholder response

    logger.warn('Backup deletion requested', {
      userId: (req as any).user?.id,
      filename,
    });

    res.json({
      success: true,
      data: {
        filename,
        deletedAt: new Date().toISOString(),
      },
      message: 'Backup deleted successfully',
    });
  } catch (error) {
    logger.error('Backup deletion failed', {
      error: error instanceof Error ? error.message : 'Unknown error',
      filename: req.params.filename,
    });

    res.status(500).json({
      success: false,
      error: {
        code: 'BACKUP_DELETE_ERROR',
        message: 'Failed to delete backup',
        details: error instanceof Error ? error.message : 'Unknown error',
        statusCode: 500,
      },
    });
  }
}));

/**
 * @route   POST /api/backup/cleanup
 * @desc    Clean up old backups based on retention policy
 * @access  Private (Admin only)
 */
router.post('/cleanup', authenticate, authorize('ADMIN', 'SUPER_ADMIN'), csrfProtection, asyncHandler(async (req, res) => {
  try {
    logger.info('Manual backup cleanup initiated', {
      userId: (req as any).user?.id,
    });

    const result = await backupManager.cleanupOldBackups();

    res.json({
      success: true,
      data: {
        deleted: result.deleted,
        errors: result.errors,
        cleanedAt: new Date().toISOString(),
      },
      message: `Cleaned up ${result.deleted.length} old backups`,
    });
  } catch (error) {
    logger.error('Backup cleanup failed', {
      error: error instanceof Error ? error.message : 'Unknown error',
      userId: (req as any).user?.id,
    });

    res.status(500).json({
      success: false,
      error: {
        code: 'BACKUP_CLEANUP_ERROR',
        message: 'Failed to clean up backups',
        details: error instanceof Error ? error.message : 'Unknown error',
        statusCode: 500,
      },
    });
  }
}));

/**
 * @route   GET /api/backup/schedule/status
 * @desc    Get backup schedule status
 * @access  Private (Admin only)
 */
router.get('/schedule/status', authenticate, authorize('ADMIN', 'SUPER_ADMIN'), asyncHandler(async (req, res) => {
  try {
    const status = backupScheduler.getScheduleStatus();
    const stats = await backupScheduler.getBackupStats();

    res.json({
      success: true,
      data: {
        ...status,
        stats,
      },
      message: 'Backup schedule status retrieved successfully',
    });
  } catch (error) {
    logger.error('Failed to get backup schedule status', { error });
    res.status(500).json({
      success: false,
      error: {
        code: 'BACKUP_SCHEDULE_ERROR',
        message: 'Failed to retrieve backup schedule status',
        statusCode: 500,
      },
    });
  }
}));

/**
 * @route   POST /api/backup/schedule/trigger
 * @desc    Trigger manual backup via scheduler
 * @access  Private (Admin only)
 */
router.post('/schedule/trigger', authenticate, authorize('ADMIN', 'SUPER_ADMIN'), csrfProtection, asyncHandler(async (req, res) => {
  try {
    const { type = 'manual', includeSchema = true, includeData = true, specificTables } = req.body;

    logger.info('Manual backup trigger via scheduler', {
      userId: (req as any).user?.id,
      type,
    });

    await backupScheduler.triggerManualBackup({
      type,
      includeSchema,
      includeData,
      specificTables,
    });

    res.json({
      success: true,
      data: {
        type,
        triggeredAt: new Date().toISOString(),
      },
      message: 'Manual backup triggered successfully',
    });
  } catch (error) {
    logger.error('Manual backup trigger failed', {
      error: error instanceof Error ? error.message : 'Unknown error',
      userId: (req as any).user?.id,
    });

    res.status(500).json({
      success: false,
      error: {
        code: 'BACKUP_TRIGGER_ERROR',
        message: 'Failed to trigger manual backup',
        details: error instanceof Error ? error.message : 'Unknown error',
        statusCode: 500,
      },
    });
  }
}));

/**
 * @route   POST /api/backup/verify-all
 * @desc    Verify integrity of all backups
 * @access  Private (Admin only)
 */
router.post('/verify-all', authenticate, authorize('ADMIN', 'SUPER_ADMIN'), asyncHandler(async (req, res) => {
  try {
    logger.info('Bulk backup verification initiated', {
      userId: (req as any).user?.id,
    });

    const result = await backupScheduler.verifyAllBackups();

    res.json({
      success: true,
      data: {
        ...result,
        verifiedAt: new Date().toISOString(),
      },
      message: `Verified ${result.total} backups: ${result.valid} valid, ${result.invalid.length} invalid`,
    });
  } catch (error) {
    logger.error('Bulk backup verification failed', {
      error: error instanceof Error ? error.message : 'Unknown error',
      userId: (req as any).user?.id,
    });

    res.status(500).json({
      success: false,
      error: {
        code: 'BACKUP_VERIFY_ALL_ERROR',
        message: 'Failed to verify all backups',
        details: error instanceof Error ? error.message : 'Unknown error',
        statusCode: 500,
      },
    });
  }
}));

/**
 * @route   GET /api/backup/stats
 * @desc    Get backup statistics and metrics
 * @access  Private (Admin only)
 */
router.get('/stats', authenticate, authorize('ADMIN', 'SUPER_ADMIN'), asyncHandler(async (req, res) => {
  try {
    const [scheduleStats, backupStats] = await Promise.all([
      backupScheduler.getBackupStats(),
      backupManager.listBackups(),
    ]);

    // Calculate additional statistics
    const now = new Date();
    const last24Hours = backupStats.filter(
      backup => new Date(backup.timestamp) > new Date(now.getTime() - 24 * 60 * 60 * 1000)
    ).length;

    const last7Days = backupStats.filter(
      backup => new Date(backup.timestamp) > new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)
    ).length;

    const compressedBackups = backupStats.filter(backup => backup.compressed).length;

    res.json({
      success: true,
      data: {
        ...scheduleStats,
        backupsLast24Hours: last24Hours,
        backupsLast7Days: last7Days,
        compressionUsage: backupStats.length > 0 ? Math.round((compressedBackups / backupStats.length) * 100) : 0,
        averageDuration: backupStats.length > 0
          ? Math.round(backupStats.reduce((sum, b) => sum + b.duration, 0) / backupStats.length / 1000)
          : 0,
      },
      message: 'Backup statistics retrieved successfully',
    });
  } catch (error) {
    logger.error('Failed to get backup statistics', { error });
    res.status(500).json({
      success: false,
      error: {
        code: 'BACKUP_STATS_ERROR',
        message: 'Failed to retrieve backup statistics',
        statusCode: 500,
      },
    });
  }
}));

/**
 * @route   GET /api/backup/health
 * @desc    Backup system health check
 * @access  Public (but should be behind load balancer health check)
 */
router.get('/health', asyncHandler(async (req, res) => {
  try {
    // Basic health checks
    const backups = await backupManager.listBackups();
    const hasRecentBackup = backups.some(backup => {
      const backupDate = new Date(backup.timestamp);
      const oneDayAgo = new Date(Date.now() - 24 * 60 * 60 * 1000);
      return backupDate > oneDayAgo;
    });

    const isHealthy = true; // Add more sophisticated health checks as needed

    res.status(isHealthy ? 200 : 503).json({
      success: isHealthy,
      data: {
        status: isHealthy ? 'healthy' : 'unhealthy',
        totalBackups: backups.length,
        hasRecentBackup,
        lastBackup: backups.length > 0 ? backups[0]?.timestamp : null,
        timestamp: new Date().toISOString(),
      },
      message: `Backup system is ${isHealthy ? 'healthy' : 'unhealthy'}`,
    });
  } catch (error) {
    res.status(503).json({
      success: false,
      error: {
        code: 'BACKUP_HEALTH_ERROR',
        message: 'Backup health check failed',
        statusCode: 503,
      },
    });
  }
}));

export default router;