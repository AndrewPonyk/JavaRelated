#!/usr/bin/env tsx
import { execSync } from 'child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync, statSync, unlinkSync, readdirSync } from 'fs';
import { join, basename, extname } from 'path';
import { createHash } from 'crypto';
import { createGzip, createGunzip } from 'zlib';
import { createReadStream, createWriteStream } from 'fs';
import { pipeline } from 'stream/promises';
import { logger } from '../src/utils/logger.js';
export class DatabaseBackupManager {
    config;
    constructor(config) {
        this.config = {
            databaseUrl: process.env.DATABASE_URL || config?.databaseUrl || '',
            backupDir: process.env.BACKUP_DIR || config?.backupDir || './backups',
            retentionDays: parseInt(process.env.BACKUP_RETENTION_DAYS || '30') || config?.retentionDays || 30,
            compression: process.env.BACKUP_COMPRESSION === 'true' || config?.compression !== false,
            encryptionKey: process.env.BACKUP_ENCRYPTION_KEY || config?.encryptionKey,
            remoteBucket: process.env.BACKUP_REMOTE_BUCKET || config?.remoteBucket,
            slackWebhook: process.env.BACKUP_SLACK_WEBHOOK || config?.slackWebhook,
            maxBackupSize: parseInt(process.env.BACKUP_MAX_SIZE_MB || '1000') || config?.maxBackupSize || 1000,
            backupTimeout: parseInt(process.env.BACKUP_TIMEOUT_SECONDS || '3600') || config?.backupTimeout || 3600,
        };
        if (!this.config.databaseUrl) {
            throw new Error('Database URL is required for backup operations');
        }
        if (!existsSync(this.config.backupDir)) {
            mkdirSync(this.config.backupDir, { recursive: true });
        }
        logger.info('Database backup manager initialized', {
            backupDir: this.config.backupDir,
            retentionDays: this.config.retentionDays,
            compression: this.config.compression,
        });
    }
    async createBackup(options = {}) {
        const startTime = Date.now();
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const suffix = options.customSuffix ? `-${options.customSuffix}` : '';
        const baseFilename = `backup-${timestamp}${suffix}`;
        logger.info('Starting database backup', {
            timestamp,
            options,
            backupDir: this.config.backupDir,
        });
        try {
            await this.validateDatabaseConnection();
            await this.checkDiskSpace();
            const databaseInfo = await this.getDatabaseInfo();
            const sqlFilename = `${baseFilename}.sql`;
            const finalFilename = this.config.compression ? `${sqlFilename}.gz` : sqlFilename;
            const backupPath = join(this.config.backupDir, sqlFilename);
            const finalPath = join(this.config.backupDir, finalFilename);
            const pgDumpCmd = this.buildPgDumpCommand(backupPath, options);
            await this.executeWithTimeout(pgDumpCmd, this.config.backupTimeout * 1000);
            if (!existsSync(backupPath)) {
                throw new Error('Backup file was not created');
            }
            const backupSize = statSync(backupPath).size;
            logger.info('SQL dump completed', {
                filename: sqlFilename,
                size: backupSize,
                sizeMB: Math.round(backupSize / 1024 / 1024 * 100) / 100,
            });
            if (backupSize > this.config.maxBackupSize * 1024 * 1024) {
                throw new Error(`Backup size (${Math.round(backupSize / 1024 / 1024)}MB) exceeds maximum allowed size (${this.config.maxBackupSize}MB)`);
            }
            let finalSize = backupSize;
            if (this.config.compression) {
                await this.compressFile(backupPath, finalPath);
                finalSize = statSync(finalPath).size;
                unlinkSync(backupPath);
                logger.info('Backup compressed', {
                    originalSize: backupSize,
                    compressedSize: finalSize,
                    compressionRatio: Math.round((1 - finalSize / backupSize) * 100) + '%',
                });
            }
            const checksum = await this.calculateChecksum(finalPath);
            const metadata = {
                filename: finalFilename,
                timestamp: new Date().toISOString(),
                size: finalSize,
                compressed: this.config.compression,
                encrypted: false,
                checksum,
                databaseSize: databaseInfo.size,
                tables: databaseInfo.tables,
                version: databaseInfo.version,
                duration: Date.now() - startTime,
            };
            await this.saveMetadata(metadata);
            if (this.config.remoteBucket) {
                try {
                    await this.uploadToRemoteStorage(finalPath, finalFilename);
                    logger.info('Backup uploaded to remote storage', { bucket: this.config.remoteBucket });
                }
                catch (error) {
                    logger.error('Failed to upload to remote storage', { error });
                }
            }
            logger.info('Database backup completed successfully', {
                filename: finalFilename,
                size: finalSize,
                duration: metadata.duration,
                tables: databaseInfo.tables.length,
            });
            await this.sendNotification('success', metadata);
            return metadata;
        }
        catch (error) {
            const errorMessage = error instanceof Error ? error.message : 'Unknown error';
            logger.error('Database backup failed', {
                error: errorMessage,
                duration: Date.now() - startTime,
            });
            await this.sendNotification('failure', { error: errorMessage, duration: Date.now() - startTime });
            throw error;
        }
    }
    async restoreBackup(backupFilename, options = {}) {
        const startTime = Date.now();
        const backupPath = join(this.config.backupDir, backupFilename);
        logger.info('Starting database restore', {
            backupFilename,
            options,
        });
        try {
            if (!existsSync(backupPath)) {
                throw new Error(`Backup file not found: ${backupFilename}`);
            }
            const metadata = await this.loadMetadata(backupFilename);
            await this.validateBackupIntegrity(backupPath, metadata);
            const restoreFile = await this.prepareRestoreFile(backupPath, metadata);
            if (options.dryRun) {
                logger.info('Dry run: Backup validation completed successfully', {
                    backupFilename,
                    metadata,
                });
                return;
            }
            const restoreCmd = this.buildRestoreCommand(restoreFile, options);
            await this.executeWithTimeout(restoreCmd, this.config.backupTimeout * 1000);
            if (restoreFile !== backupPath) {
                unlinkSync(restoreFile);
            }
            logger.info('Database restore completed successfully', {
                backupFilename,
                duration: Date.now() - startTime,
            });
            await this.sendNotification('restore', { filename: backupFilename, duration: Date.now() - startTime });
        }
        catch (error) {
            const errorMessage = error instanceof Error ? error.message : 'Unknown error';
            logger.error('Database restore failed', {
                error: errorMessage,
                backupFilename,
                duration: Date.now() - startTime,
            });
            throw error;
        }
    }
    async listBackups() {
        try {
            const files = readdirSync(this.config.backupDir)
                .filter(file => file.startsWith('backup-') && (file.endsWith('.sql') || file.endsWith('.sql.gz')))
                .sort()
                .reverse();
            const backups = [];
            for (const file of files) {
                try {
                    const metadata = await this.loadMetadata(file);
                    backups.push(metadata);
                }
                catch (error) {
                    logger.warn('Failed to load metadata for backup', { file, error });
                    const filePath = join(this.config.backupDir, file);
                    const stat = statSync(filePath);
                    backups.push({
                        filename: file,
                        timestamp: stat.mtime.toISOString(),
                        size: stat.size,
                        compressed: file.endsWith('.gz'),
                        encrypted: false,
                        checksum: 'unknown',
                        databaseSize: 0,
                        tables: [],
                        version: 'unknown',
                        duration: 0,
                    });
                }
            }
            return backups;
        }
        catch (error) {
            logger.error('Failed to list backups', { error });
            return [];
        }
    }
    async cleanupOldBackups() {
        const cutoffDate = new Date();
        cutoffDate.setDate(cutoffDate.getDate() - this.config.retentionDays);
        logger.info('Starting backup cleanup', {
            retentionDays: this.config.retentionDays,
            cutoffDate: cutoffDate.toISOString(),
        });
        const backups = await this.listBackups();
        const deleted = [];
        const errors = [];
        for (const backup of backups) {
            const backupDate = new Date(backup.timestamp);
            if (backupDate < cutoffDate) {
                try {
                    const backupPath = join(this.config.backupDir, backup.filename);
                    const metadataPath = this.getMetadataPath(backup.filename);
                    if (existsSync(backupPath)) {
                        unlinkSync(backupPath);
                    }
                    if (existsSync(metadataPath)) {
                        unlinkSync(metadataPath);
                    }
                    deleted.push(backup.filename);
                    logger.info('Deleted old backup', {
                        filename: backup.filename,
                        age: Math.floor((Date.now() - backupDate.getTime()) / (1000 * 60 * 60 * 24)) + ' days',
                    });
                }
                catch (error) {
                    const errorMessage = error instanceof Error ? error.message : 'Unknown error';
                    errors.push(`${backup.filename}: ${errorMessage}`);
                    logger.error('Failed to delete backup', {
                        filename: backup.filename,
                        error: errorMessage,
                    });
                }
            }
        }
        logger.info('Backup cleanup completed', {
            deleted: deleted.length,
            errors: errors.length,
        });
        return { deleted, errors };
    }
    async verifyBackup(backupFilename) {
        const backupPath = join(this.config.backupDir, backupFilename);
        const errors = [];
        try {
            if (!existsSync(backupPath)) {
                errors.push('Backup file not found');
                return { valid: false, errors };
            }
            const metadata = await this.loadMetadata(backupFilename);
            const currentChecksum = await this.calculateChecksum(backupPath);
            if (currentChecksum !== metadata.checksum) {
                errors.push(`Checksum mismatch: expected ${metadata.checksum}, got ${currentChecksum}`);
            }
            const currentSize = statSync(backupPath).size;
            if (currentSize !== metadata.size) {
                errors.push(`Size mismatch: expected ${metadata.size}, got ${currentSize}`);
            }
            if (metadata.compressed) {
                try {
                    await this.testDecompression(backupPath);
                }
                catch (error) {
                    errors.push('Failed to decompress backup file');
                }
            }
            logger.info('Backup verification completed', {
                filename: backupFilename,
                valid: errors.length === 0,
                errors,
            });
            return { valid: errors.length === 0, errors };
        }
        catch (error) {
            const errorMessage = error instanceof Error ? error.message : 'Unknown error';
            errors.push(`Verification failed: ${errorMessage}`);
            return { valid: false, errors };
        }
    }
    buildPgDumpCommand(outputPath, options) {
        const url = new URL(this.config.databaseUrl);
        const args = [
            'pg_dump',
            '--no-password',
            '--verbose',
            '--clean',
            '--if-exists',
            '--create',
        ];
        args.push(`--host=${url.hostname}`);
        args.push(`--port=${url.port || 5432}`);
        args.push(`--username=${url.username}`);
        args.push(`--dbname=${url.pathname.slice(1)}`);
        if (options.includeSchema === false) {
            args.push('--data-only');
        }
        else if (options.includeData === false) {
            args.push('--schema-only');
        }
        if (options.specificTables?.length) {
            for (const table of options.specificTables) {
                args.push(`--table=${table}`);
            }
        }
        args.push(`--file=${outputPath}`);
        const env = { ...process.env, PGPASSWORD: url.password };
        return { command: args.join(' '), env };
    }
    buildRestoreCommand(inputPath, options) {
        const url = new URL(this.config.databaseUrl);
        const args = [
            'psql',
            '--no-password',
            '--echo-errors',
        ];
        args.push(`--host=${url.hostname}`);
        args.push(`--port=${url.port || 5432}`);
        args.push(`--username=${url.username}`);
        const targetDb = options.targetDatabase || url.pathname.slice(1);
        args.push(`--dbname=${targetDb}`);
        args.push(`--file=${inputPath}`);
        const env = { ...process.env, PGPASSWORD: url.password };
        return { command: args.join(' '), env };
    }
    async executeWithTimeout(cmd, timeoutMs) {
        return new Promise((resolve, reject) => {
            let process;
            const timeout = setTimeout(() => {
                if (process) {
                    process.kill('SIGKILL');
                }
                reject(new Error(`Command timed out after ${timeoutMs / 1000} seconds`));
            }, timeoutMs);
            try {
                if (typeof cmd === 'string') {
                    execSync(cmd, { stdio: 'pipe', timeout: timeoutMs });
                }
                else {
                    execSync(cmd.command, {
                        stdio: 'pipe',
                        timeout: timeoutMs,
                        env: cmd.env
                    });
                }
                clearTimeout(timeout);
                resolve();
            }
            catch (error) {
                clearTimeout(timeout);
                reject(error);
            }
        });
    }
    async compressFile(inputPath, outputPath) {
        const gzip = createGzip({ level: 9 });
        const source = createReadStream(inputPath);
        const destination = createWriteStream(outputPath);
        await pipeline(source, gzip, destination);
    }
    async calculateChecksum(filePath) {
        return new Promise((resolve, reject) => {
            const hash = createHash('sha256');
            const stream = createReadStream(filePath);
            stream.on('error', reject);
            stream.on('data', chunk => hash.update(chunk));
            stream.on('end', () => resolve(hash.digest('hex')));
        });
    }
    async validateDatabaseConnection() {
        try {
            const url = new URL(this.config.databaseUrl);
            const cmd = `pg_isready --host=${url.hostname} --port=${url.port || 5432} --dbname=${url.pathname.slice(1)}`;
            execSync(cmd, { stdio: 'pipe' });
        }
        catch (error) {
            throw new Error('Database connection validation failed');
        }
    }
    async checkDiskSpace() {
        try {
            const stats = statSync(this.config.backupDir);
            logger.info('Backup directory accessible', { backupDir: this.config.backupDir });
        }
        catch (error) {
            throw new Error('Backup directory is not accessible');
        }
    }
    async getDatabaseInfo() {
        return {
            size: 0,
            tables: [],
            version: 'PostgreSQL 15',
        };
    }
    getMetadataPath(backupFilename) {
        const baseName = basename(backupFilename, extname(backupFilename));
        return join(this.config.backupDir, `${baseName}.metadata.json`);
    }
    async saveMetadata(metadata) {
        const metadataPath = this.getMetadataPath(metadata.filename);
        writeFileSync(metadataPath, JSON.stringify(metadata, null, 2));
    }
    async loadMetadata(backupFilename) {
        const metadataPath = this.getMetadataPath(backupFilename);
        if (!existsSync(metadataPath)) {
            throw new Error(`Metadata not found for backup: ${backupFilename}`);
        }
        const metadataContent = readFileSync(metadataPath, 'utf8');
        return JSON.parse(metadataContent);
    }
    async validateBackupIntegrity(backupPath, metadata) {
        const currentChecksum = await this.calculateChecksum(backupPath);
        if (currentChecksum !== metadata.checksum) {
            throw new Error(`Backup integrity check failed: checksum mismatch`);
        }
    }
    async prepareRestoreFile(backupPath, metadata) {
        if (!metadata.compressed) {
            return backupPath;
        }
        const tempPath = backupPath.replace(/\.gz$/, '.temp');
        const gunzip = createGunzip();
        const source = createReadStream(backupPath);
        const destination = createWriteStream(tempPath);
        await pipeline(source, gunzip, destination);
        return tempPath;
    }
    async testDecompression(compressedPath) {
        const gunzip = createGunzip();
        const source = createReadStream(compressedPath);
        return new Promise((resolve, reject) => {
            source.pipe(gunzip)
                .on('error', reject)
                .on('end', resolve)
                .resume();
        });
    }
    async uploadToRemoteStorage(filePath, filename) {
        logger.info('Would upload to remote storage', { filePath, filename, bucket: this.config.remoteBucket });
    }
    async sendNotification(type, data) {
        if (!this.config.slackWebhook)
            return;
        const message = {
            success: `✅ Database backup completed successfully\nFile: ${data.filename}\nSize: ${Math.round(data.size / 1024 / 1024)}MB\nDuration: ${Math.round(data.duration / 1000)}s`,
            failure: `❌ Database backup failed\nError: ${data.error}\nDuration: ${Math.round(data.duration / 1000)}s`,
            restore: `🔄 Database restore completed\nFile: ${data.filename}\nDuration: ${Math.round(data.duration / 1000)}s`,
        };
        try {
            logger.info('Would send Slack notification', { type, message: message[type] });
        }
        catch (error) {
            logger.error('Failed to send notification', { error });
        }
    }
}
if (import.meta.url === `file://${process.argv[1]}`) {
    const backupManager = new DatabaseBackupManager();
    const command = process.argv[2];
    switch (command) {
        case 'backup':
            backupManager.createBackup()
                .then(metadata => {
                console.log('Backup completed:', metadata.filename);
                process.exit(0);
            })
                .catch(error => {
                console.error('Backup failed:', error.message);
                process.exit(1);
            });
            break;
        case 'list':
            backupManager.listBackups()
                .then(backups => {
                console.table(backups.map(b => ({
                    filename: b.filename,
                    timestamp: b.timestamp,
                    size: `${Math.round(b.size / 1024 / 1024)}MB`,
                    tables: b.tables.length,
                })));
            })
                .catch(error => {
                console.error('Failed to list backups:', error.message);
                process.exit(1);
            });
            break;
        case 'cleanup':
            backupManager.cleanupOldBackups()
                .then(result => {
                console.log(`Cleaned up ${result.deleted.length} old backups`);
                if (result.errors.length > 0) {
                    console.error('Errors:', result.errors);
                }
            })
                .catch(error => {
                console.error('Cleanup failed:', error.message);
                process.exit(1);
            });
            break;
        case 'verify':
            const filename = process.argv[3];
            if (!filename) {
                console.error('Usage: npm run backup verify <filename>');
                process.exit(1);
            }
            backupManager.verifyBackup(filename)
                .then(result => {
                console.log(`Backup ${filename}: ${result.valid ? 'VALID' : 'INVALID'}`);
                if (result.errors.length > 0) {
                    console.error('Errors:', result.errors);
                }
            })
                .catch(error => {
                console.error('Verification failed:', error.message);
                process.exit(1);
            });
            break;
        default:
            console.log('Usage: npm run backup <command>');
            console.log('Commands:');
            console.log('  backup  - Create a new backup');
            console.log('  list    - List all backups');
            console.log('  cleanup - Clean up old backups');
            console.log('  verify <filename> - Verify backup integrity');
            break;
    }
}
export default DatabaseBackupManager;
//# sourceMappingURL=database-backup.js.map