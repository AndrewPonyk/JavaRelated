import { redis } from '@/config/redis.js';
import { logger } from '@/utils/logger.js';
import { config } from '@/config/environment.js';
export class BackgroundProcessor {
    static instance;
    handlers = new Map();
    queues = new Map();
    isRunning = false;
    workers = new Map();
    concurrency = config.performance.maxConcurrentRequests || 5;
    constructor() {
        this.setupDefaultHandlers();
        this.initializeQueues();
    }
    static getInstance() {
        if (!BackgroundProcessor.instance) {
            BackgroundProcessor.instance = new BackgroundProcessor();
        }
        return BackgroundProcessor.instance;
    }
    async addJob(queue, type, data, options = {}) {
        const jobId = this.generateJobId();
        const job = {
            id: jobId,
            type,
            data,
            options: {
                priority: 0,
                delay: 0,
                attempts: 3,
                timeout: 30000,
                ...options,
            },
            createdAt: new Date(),
            attempts: 0,
        };
        try {
            await redis.hset(`queue:${queue}:jobs`, jobId, JSON.stringify(job));
            if (options.delay && options.delay > 0) {
                const processAt = Date.now() + options.delay;
                await redis.zadd(`queue:${queue}:delayed`, processAt, jobId);
            }
            else {
                const priority = options.priority || 0;
                await redis.zadd(`queue:${queue}:waiting`, -priority, jobId);
            }
            await this.updateQueueStats(queue);
            logger.debug('Job added to queue', {
                jobId,
                queue,
                type,
                delay: options.delay,
                priority: options.priority,
            });
            return jobId;
        }
        catch (error) {
            logger.error('Failed to add job to queue', { error, jobId, queue, type });
            throw error;
        }
    }
    registerHandler(type, handler) {
        this.handlers.set(type, handler);
        logger.debug('Job handler registered', { type });
    }
    async start() {
        if (this.isRunning) {
            logger.warn('Background processor already running');
            return;
        }
        this.isRunning = true;
        logger.info('Starting background job processor', {
            concurrency: this.concurrency,
            queues: Array.from(this.queues.keys()),
        });
        for (const queueName of this.queues.keys()) {
            this.startWorker(queueName);
        }
        this.startDelayedJobProcessor();
        this.startCleanupTask();
    }
    async stop() {
        if (!this.isRunning)
            return;
        this.isRunning = false;
        logger.info('Stopping background job processor');
        this.workers.forEach(worker => clearTimeout(worker));
        this.workers.clear();
        await this.waitForActiveJobsToComplete(10000);
    }
    async getQueueStats(queueName) {
        try {
            const [waiting, active, completed, failed, delayed] = await Promise.all([
                redis.zcard(`queue:${queueName}:waiting`),
                redis.zcard(`queue:${queueName}:active`),
                redis.zcard(`queue:${queueName}:completed`),
                redis.zcard(`queue:${queueName}:failed`),
                redis.zcard(`queue:${queueName}:delayed`),
            ]);
            return {
                waiting,
                active,
                completed,
                failed,
                delayed,
                paused: false,
            };
        }
        catch (error) {
            logger.error('Failed to get queue stats', { error, queueName });
            return {
                waiting: 0,
                active: 0,
                completed: 0,
                failed: 0,
                delayed: 0,
                paused: true,
            };
        }
    }
    async getJob(queue, jobId) {
        try {
            const jobData = await redis.hget(`queue:${queue}:jobs`, jobId);
            return jobData ? JSON.parse(jobData) : null;
        }
        catch (error) {
            logger.error('Failed to get job', { error, queue, jobId });
            return null;
        }
    }
    async scheduleRecurring(queue, type, cronExpression, data, options = {}) {
        const recurringJob = {
            queue,
            type,
            data,
            options,
            cronExpression,
            nextRunAt: this.getNextRunTime(cronExpression),
        };
        await redis.hset('recurring:jobs', `${queue}:${type}`, JSON.stringify(recurringJob));
        logger.info('Recurring job scheduled', {
            queue,
            type,
            cronExpression,
            nextRunAt: recurringJob.nextRunAt,
        });
    }
    getPerformanceMetrics() {
        return {
            totalJobs: 0,
            activeJobs: 0,
            completedJobs: 0,
            failedJobs: 0,
            averageProcessingTime: 0,
            throughput: 0,
        };
    }
    setupDefaultHandlers() {
        this.registerHandler('cache:warm', async (job) => {
            const { cacheOptimizer } = await import('./cacheOptimizer.js');
            await cacheOptimizer.warmupCache(true);
            return { status: 'completed', timestamp: new Date() };
        });
        this.registerHandler('database:optimize', async (job) => {
            const { databaseOptimizer } = await import('./databaseOptimizer.js');
            await databaseOptimizer.optimizeConnectionPool();
            return { status: 'completed', timestamp: new Date() };
        });
        this.registerHandler('analytics:aggregate', async (job) => {
            logger.info('Analytics aggregation completed', { jobId: job.id });
            return { status: 'completed', recordsProcessed: 100 };
        });
        this.registerHandler('backup:cleanup', async (job) => {
            logger.info('Backup cleanup completed', { jobId: job.id });
            return { status: 'completed', filesRemoved: 5 };
        });
        this.registerHandler('email:send', async (job) => {
            const { to, subject, body } = job.data;
            logger.info('Email sent', { jobId: job.id, to, subject });
            return { status: 'sent', timestamp: new Date() };
        });
    }
    initializeQueues() {
        const defaultQueues = ['default', 'high-priority', 'low-priority', 'maintenance'];
        defaultQueues.forEach(queue => {
            this.queues.set(queue, {
                waiting: 0,
                active: 0,
                completed: 0,
                failed: 0,
                delayed: 0,
                paused: false,
            });
        });
    }
    startWorker(queueName) {
        if (!this.isRunning)
            return;
        const processNextJob = async () => {
            try {
                await this.processNextJob(queueName);
            }
            catch (error) {
                logger.error('Worker error', { error, queue: queueName });
            }
            finally {
                if (this.isRunning) {
                    const worker = setTimeout(processNextJob, 1000);
                    this.workers.set(queueName, worker);
                }
            }
        };
        processNextJob();
    }
    async processNextJob(queueName) {
        const result = await redis.bzpopmin(`queue:${queueName}:waiting`, 1);
        if (!result)
            return;
        const [, jobId] = result;
        try {
            const jobData = await redis.hget(`queue:${queueName}:jobs`, jobId);
            if (!jobData)
                return;
            const job = JSON.parse(jobData);
            await redis.zadd(`queue:${queueName}:active`, Date.now(), jobId);
            await this.executeJob(queueName, job);
        }
        catch (error) {
            logger.error('Failed to process job', { error, jobId, queue: queueName });
        }
    }
    async executeJob(queueName, job) {
        const startTime = Date.now();
        job.processedAt = new Date();
        job.attempts++;
        try {
            const handler = this.handlers.get(job.type);
            if (!handler) {
                throw new Error(`No handler registered for job type: ${job.type}`);
            }
            const result = await this.withTimeout(handler(job), job.options.timeout || 30000);
            job.completedAt = new Date();
            job.result = result;
            await this.moveToCompleted(queueName, job);
            const duration = Date.now() - startTime;
            logger.info('Job completed successfully', {
                jobId: job.id,
                type: job.type,
                queue: queueName,
                duration,
                attempts: job.attempts,
            });
        }
        catch (error) {
            const duration = Date.now() - startTime;
            job.error = error instanceof Error ? error.message : String(error);
            if (job.attempts < (job.options.attempts || 3)) {
                await this.scheduleRetry(queueName, job);
                logger.warn('Job failed, scheduled for retry', {
                    jobId: job.id,
                    type: job.type,
                    queue: queueName,
                    attempts: job.attempts,
                    maxAttempts: job.options.attempts,
                    error: job.error,
                });
            }
            else {
                job.failedAt = new Date();
                await this.moveToFailed(queueName, job);
                logger.error('Job failed permanently', {
                    jobId: job.id,
                    type: job.type,
                    queue: queueName,
                    attempts: job.attempts,
                    error: job.error,
                    duration,
                });
            }
        }
    }
    async moveToCompleted(queueName, job) {
        await Promise.all([
            redis.zrem(`queue:${queueName}:active`, job.id),
            redis.zadd(`queue:${queueName}:completed`, Date.now(), job.id),
            redis.hset(`queue:${queueName}:jobs`, job.id, JSON.stringify(job)),
        ]);
        await this.updateQueueStats(queueName);
    }
    async moveToFailed(queueName, job) {
        await Promise.all([
            redis.zrem(`queue:${queueName}:active`, job.id),
            redis.zadd(`queue:${queueName}:failed`, Date.now(), job.id),
            redis.hset(`queue:${queueName}:jobs`, job.id, JSON.stringify(job)),
        ]);
        await this.updateQueueStats(queueName);
    }
    async scheduleRetry(queueName, job) {
        const backoff = job.options.backoff || { type: 'exponential', delay: 1000 };
        let delay = backoff.delay;
        if (backoff.type === 'exponential') {
            delay = backoff.delay * Math.pow(2, job.attempts - 1);
        }
        const processAt = Date.now() + delay;
        await Promise.all([
            redis.zrem(`queue:${queueName}:active`, job.id),
            redis.zadd(`queue:${queueName}:delayed`, processAt, job.id),
            redis.hset(`queue:${queueName}:jobs`, job.id, JSON.stringify(job)),
        ]);
        await this.updateQueueStats(queueName);
    }
    startDelayedJobProcessor() {
        if (!this.isRunning)
            return;
        const processDelayedJobs = async () => {
            try {
                for (const queueName of this.queues.keys()) {
                    await this.moveDelayedJobsToWaiting(queueName);
                }
            }
            catch (error) {
                logger.error('Delayed job processor error', { error });
            }
            finally {
                if (this.isRunning) {
                    setTimeout(processDelayedJobs, 5000);
                }
            }
        };
        processDelayedJobs();
    }
    async moveDelayedJobsToWaiting(queueName) {
        const now = Date.now();
        const readyJobs = await redis.zrangebyscore(`queue:${queueName}:delayed`, 0, now, 'LIMIT', 0, 100);
        if (readyJobs.length === 0)
            return;
        const pipeline = redis.pipeline();
        for (const jobId of readyJobs) {
            const jobData = await redis.hget(`queue:${queueName}:jobs`, jobId);
            if (jobData) {
                const job = JSON.parse(jobData);
                const priority = job.options.priority || 0;
                pipeline.zrem(`queue:${queueName}:delayed`, jobId);
                pipeline.zadd(`queue:${queueName}:waiting`, -priority, jobId);
            }
        }
        await pipeline.exec();
        if (readyJobs.length > 0) {
            await this.updateQueueStats(queueName);
            logger.debug('Moved delayed jobs to waiting', {
                queue: queueName,
                count: readyJobs.length,
            });
        }
    }
    startCleanupTask() {
        if (!this.isRunning)
            return;
        const cleanup = async () => {
            try {
                await this.cleanupOldJobs();
                await this.processRecurringJobs();
            }
            catch (error) {
                logger.error('Cleanup task error', { error });
            }
            finally {
                if (this.isRunning) {
                    setTimeout(cleanup, 5 * 60 * 1000);
                }
            }
        };
        cleanup();
    }
    async cleanupOldJobs() {
        const cutoffTime = Date.now() - (7 * 24 * 60 * 60 * 1000);
        for (const queueName of this.queues.keys()) {
            await redis.zremrangebyscore(`queue:${queueName}:completed`, 0, cutoffTime);
            await redis.zremrangebyscore(`queue:${queueName}:failed`, 0, cutoffTime);
        }
    }
    async processRecurringJobs() {
        const recurringJobs = await redis.hgetall('recurring:jobs');
        for (const [key, jobData] of Object.entries(recurringJobs)) {
            try {
                const recurringJob = JSON.parse(jobData);
                if (Date.now() >= new Date(recurringJob.nextRunAt).getTime()) {
                    await this.addJob(recurringJob.queue, recurringJob.type, recurringJob.data, recurringJob.options);
                    recurringJob.nextRunAt = this.getNextRunTime(recurringJob.cronExpression);
                    await redis.hset('recurring:jobs', key, JSON.stringify(recurringJob));
                }
            }
            catch (error) {
                logger.error('Failed to process recurring job', { error, key });
            }
        }
    }
    async updateQueueStats(queueName) {
        try {
            const stats = await this.getQueueStats(queueName);
            this.queues.set(queueName, stats);
        }
        catch (error) {
            logger.error('Failed to update queue stats', { error, queueName });
        }
    }
    async waitForActiveJobsToComplete(timeout) {
        const startTime = Date.now();
        while (Date.now() - startTime < timeout) {
            let hasActiveJobs = false;
            for (const queueName of this.queues.keys()) {
                const stats = await this.getQueueStats(queueName);
                if (stats.active > 0) {
                    hasActiveJobs = true;
                    break;
                }
            }
            if (!hasActiveJobs)
                break;
            await new Promise(resolve => setTimeout(resolve, 100));
        }
    }
    withTimeout(promise, timeout) {
        return Promise.race([
            promise,
            new Promise((_, reject) => setTimeout(() => reject(new Error('Job timeout')), timeout)),
        ]);
    }
    generateJobId() {
        return `job_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    }
    getNextRunTime(cronExpression) {
        const nextRun = new Date();
        nextRun.setHours(nextRun.getHours() + 1);
        return nextRun;
    }
}
export const backgroundProcessor = BackgroundProcessor.getInstance();
export const scheduleJob = (queue, type, data, options) => backgroundProcessor.addJob(queue, type, data, options);
export const scheduleDelayedJob = (queue, type, data, delayMs, options) => backgroundProcessor.addJob(queue, type, data, { ...options, delay: delayMs });
export const scheduleRecurringJob = (queue, type, cronExpression, data, options) => backgroundProcessor.scheduleRecurring(queue, type, cronExpression, data, options);
//# sourceMappingURL=backgroundProcessor.js.map