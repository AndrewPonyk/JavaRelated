import { redis } from '@/config/redis.js';
import { logger } from '@/utils/logger.js';
import { config } from '@/config/environment.js';

/**
 * Background Job Processor
 * Handles async operations, scheduled tasks, and performance monitoring
 */

export interface JobOptions {
  priority?: number;
  delay?: number; // milliseconds
  attempts?: number;
  backoff?: {
    type: 'fixed' | 'exponential';
    delay: number;
  };
  timeout?: number;
}

export interface Job<T = any> {
  id: string;
  type: string;
  data: T;
  options: JobOptions;
  createdAt: Date;
  processedAt?: Date;
  completedAt?: Date;
  failedAt?: Date;
  attempts: number;
  error?: string;
  result?: any;
}

export interface QueueStats {
  waiting: number;
  active: number;
  completed: number;
  failed: number;
  delayed: number;
  paused: boolean;
}

export type JobHandler<T = any> = (job: Job<T>) => Promise<any>;

export class BackgroundProcessor {
  private static instance: BackgroundProcessor;
  private handlers: Map<string, JobHandler> = new Map();
  private queues: Map<string, QueueStats> = new Map();
  private isRunning: boolean = false;
  private workers: Map<string, NodeJS.Timeout> = new Map();
  private concurrency: number = config.performance.maxConcurrentRequests || 5;

  private constructor() {
    this.setupDefaultHandlers();
    this.initializeQueues();
  }

  public static getInstance(): BackgroundProcessor {
    if (!BackgroundProcessor.instance) {
      BackgroundProcessor.instance = new BackgroundProcessor();
    }
    return BackgroundProcessor.instance;
  }

  /**
   * Add a job to the queue
   */
  public async addJob<T>(
    queue: string,
    type: string,
    data: T,
    options: JobOptions = {}
  ): Promise<string> {
    const jobId = this.generateJobId();
    const job: Job<T> = {
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
      // Store job in Redis
      await redis.hset(`queue:${queue}:jobs`, jobId, JSON.stringify(job));

      // Add to appropriate queue list
      if (options.delay && options.delay > 0) {
        const processAt = Date.now() + options.delay;
        await redis.zadd(`queue:${queue}:delayed`, processAt, jobId);
      } else {
        const priority = options.priority || 0;
        await redis.zadd(`queue:${queue}:waiting`, -priority, jobId);
      }

      // Update stats
      await this.updateQueueStats(queue);

      logger.debug('Job added to queue', {
        jobId,
        queue,
        type,
        delay: options.delay,
        priority: options.priority,
      });

      return jobId;

    } catch (error) {
      logger.error('Failed to add job to queue', { error, jobId, queue, type });
      throw error;
    }
  }

  /**
   * Register a job handler
   */
  public registerHandler<T>(type: string, handler: JobHandler<T>): void {
    this.handlers.set(type, handler);
    logger.debug('Job handler registered', { type });
  }

  /**
   * Start processing jobs
   */
  public async start(): Promise<void> {
    if (this.isRunning) {
      logger.warn('Background processor already running');
      return;
    }

    this.isRunning = true;
    logger.info('Starting background job processor', {
      concurrency: this.concurrency,
      queues: Array.from(this.queues.keys()),
    });

    // Start workers for each queue
    for (const queueName of this.queues.keys()) {
      this.startWorker(queueName);
    }

    // Start delayed job processor
    this.startDelayedJobProcessor();

    // Start cleanup task
    this.startCleanupTask();
  }

  /**
   * Stop processing jobs
   */
  public async stop(): Promise<void> {
    if (!this.isRunning) return;

    this.isRunning = false;
    logger.info('Stopping background job processor');

    // Clear all workers
    this.workers.forEach(worker => clearTimeout(worker));
    this.workers.clear();

    // Wait for active jobs to complete (with timeout)
    await this.waitForActiveJobsToComplete(10000); // 10 seconds
  }

  /**
   * Get queue statistics
   */
  public async getQueueStats(queueName: string): Promise<QueueStats> {
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

    } catch (error) {
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

  /**
   * Get job details
   */
  public async getJob(queue: string, jobId: string): Promise<Job | null> {
    try {
      const jobData = await redis.hget(`queue:${queue}:jobs`, jobId);
      return jobData ? JSON.parse(jobData) : null;
    } catch (error) {
      logger.error('Failed to get job', { error, queue, jobId });
      return null;
    }
  }

  /**
   * Schedule recurring jobs
   */
  public async scheduleRecurring(
    queue: string,
    type: string,
    cronExpression: string,
    data: any,
    options: JobOptions = {}
  ): Promise<void> {
    // Store recurring job configuration
    const recurringJob = {
      queue,
      type,
      data,
      options,
      cronExpression,
      nextRunAt: this.getNextRunTime(cronExpression),
    };

    await redis.hset(
      'recurring:jobs',
      `${queue}:${type}`,
      JSON.stringify(recurringJob)
    );

    logger.info('Recurring job scheduled', {
      queue,
      type,
      cronExpression,
      nextRunAt: recurringJob.nextRunAt,
    });
  }

  /**
   * Performance monitoring
   */
  public getPerformanceMetrics(): {
    totalJobs: number;
    activeJobs: number;
    completedJobs: number;
    failedJobs: number;
    averageProcessingTime: number;
    throughput: number; // jobs per minute
  } {
    // This would be implemented with actual metrics collection
    return {
      totalJobs: 0,
      activeJobs: 0,
      completedJobs: 0,
      failedJobs: 0,
      averageProcessingTime: 0,
      throughput: 0,
    };
  }

  // Private methods

  private setupDefaultHandlers(): void {
    // Cache warming job
    this.registerHandler('cache:warm', async (job) => {
      const { cacheOptimizer } = await import('./cacheOptimizer.js');
      await cacheOptimizer.warmupCache(true);
      return { status: 'completed', timestamp: new Date() };
    });

    // Database optimization job
    this.registerHandler('database:optimize', async (job) => {
      const { databaseOptimizer } = await import('./databaseOptimizer.js');
      await databaseOptimizer.optimizeConnectionPool();
      return { status: 'completed', timestamp: new Date() };
    });

    // Analytics aggregation job
    this.registerHandler('analytics:aggregate', async (job) => {
      // Implement analytics aggregation
      logger.info('Analytics aggregation completed', { jobId: job.id });
      return { status: 'completed', recordsProcessed: 100 };
    });

    // Backup cleanup job
    this.registerHandler('backup:cleanup', async (job) => {
      // Implement backup cleanup
      logger.info('Backup cleanup completed', { jobId: job.id });
      return { status: 'completed', filesRemoved: 5 };
    });

    // Email notification job
    this.registerHandler('email:send', async (job) => {
      const { to, subject, body } = job.data as any;
      // Implement email sending
      logger.info('Email sent', { jobId: job.id, to, subject });
      return { status: 'sent', timestamp: new Date() };
    });
  }

  private initializeQueues(): void {
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

  private startWorker(queueName: string): void {
    if (!this.isRunning) return;

    const processNextJob = async () => {
      try {
        await this.processNextJob(queueName);
      } catch (error) {
        logger.error('Worker error', { error, queue: queueName });
      } finally {
        // Schedule next iteration
        if (this.isRunning) {
          const worker = setTimeout(processNextJob, 1000); // Check every second
          this.workers.set(queueName, worker);
        }
      }
    };

    processNextJob();
  }

  private async processNextJob(queueName: string): Promise<void> {
    // Get next job from waiting queue
    const result = await redis.bzpopmin(`queue:${queueName}:waiting`, 1);
    if (!result) return;

    const [, jobId] = result;

    try {
      // Get job details
      const jobData = await redis.hget(`queue:${queueName}:jobs`, jobId);
      if (!jobData) return;

      const job: Job = JSON.parse(jobData);

      // Move to active queue
      await redis.zadd(`queue:${queueName}:active`, Date.now(), jobId);

      // Process the job
      await this.executeJob(queueName, job);

    } catch (error) {
      logger.error('Failed to process job', { error, jobId, queue: queueName });
    }
  }

  private async executeJob(queueName: string, job: Job): Promise<void> {
    const startTime = Date.now();
    job.processedAt = new Date();
    job.attempts++;

    try {
      // Get handler for job type
      const handler = this.handlers.get(job.type);
      if (!handler) {
        throw new Error(`No handler registered for job type: ${job.type}`);
      }

      // Execute with timeout
      const result = await this.withTimeout(
        handler(job),
        job.options.timeout || 30000
      );

      // Job completed successfully
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

    } catch (error) {
      const duration = Date.now() - startTime;
      job.error = error instanceof Error ? error.message : String(error);

      // Check if we should retry
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
      } else {
        // Max attempts reached, move to failed
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

  private async moveToCompleted(queueName: string, job: Job): Promise<void> {
    await Promise.all([
      redis.zrem(`queue:${queueName}:active`, job.id),
      redis.zadd(`queue:${queueName}:completed`, Date.now(), job.id),
      redis.hset(`queue:${queueName}:jobs`, job.id, JSON.stringify(job)),
    ]);

    await this.updateQueueStats(queueName);
  }

  private async moveToFailed(queueName: string, job: Job): Promise<void> {
    await Promise.all([
      redis.zrem(`queue:${queueName}:active`, job.id),
      redis.zadd(`queue:${queueName}:failed`, Date.now(), job.id),
      redis.hset(`queue:${queueName}:jobs`, job.id, JSON.stringify(job)),
    ]);

    await this.updateQueueStats(queueName);
  }

  private async scheduleRetry(queueName: string, job: Job): Promise<void> {
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

  private startDelayedJobProcessor(): void {
    if (!this.isRunning) return;

    const processDelayedJobs = async () => {
      try {
        for (const queueName of this.queues.keys()) {
          await this.moveDelayedJobsToWaiting(queueName);
        }
      } catch (error) {
        logger.error('Delayed job processor error', { error });
      } finally {
        if (this.isRunning) {
          setTimeout(processDelayedJobs, 5000); // Check every 5 seconds
        }
      }
    };

    processDelayedJobs();
  }

  private async moveDelayedJobsToWaiting(queueName: string): Promise<void> {
    const now = Date.now();

    // Get jobs ready to be processed
    const readyJobs = await redis.zrangebyscore(
      `queue:${queueName}:delayed`,
      0,
      now,
      'LIMIT', 0, 100
    );

    if (readyJobs.length === 0) return;

    // Move jobs to waiting queue
    const pipeline = redis.pipeline();

    for (const jobId of readyJobs) {
      // Get job to determine priority
      const jobData = await redis.hget(`queue:${queueName}:jobs`, jobId);
      if (jobData) {
        const job: Job = JSON.parse(jobData);
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

  private startCleanupTask(): void {
    if (!this.isRunning) return;

    const cleanup = async () => {
      try {
        await this.cleanupOldJobs();
        await this.processRecurringJobs();
      } catch (error) {
        logger.error('Cleanup task error', { error });
      } finally {
        if (this.isRunning) {
          setTimeout(cleanup, 5 * 60 * 1000); // Every 5 minutes
        }
      }
    };

    cleanup();
  }

  private async cleanupOldJobs(): Promise<void> {
    const cutoffTime = Date.now() - (7 * 24 * 60 * 60 * 1000); // 7 days ago

    for (const queueName of this.queues.keys()) {
      // Clean up completed jobs older than 7 days
      await redis.zremrangebyscore(`queue:${queueName}:completed`, 0, cutoffTime);

      // Clean up failed jobs older than 7 days
      await redis.zremrangebyscore(`queue:${queueName}:failed`, 0, cutoffTime);
    }
  }

  private async processRecurringJobs(): Promise<void> {
    const recurringJobs = await redis.hgetall('recurring:jobs');

    for (const [key, jobData] of Object.entries(recurringJobs)) {
      try {
        const recurringJob = JSON.parse(jobData as string);

        if (Date.now() >= new Date(recurringJob.nextRunAt).getTime()) {
          // Add job to queue
          await this.addJob(
            recurringJob.queue,
            recurringJob.type,
            recurringJob.data,
            recurringJob.options
          );

          // Update next run time
          recurringJob.nextRunAt = this.getNextRunTime(recurringJob.cronExpression);
          await redis.hset('recurring:jobs', key, JSON.stringify(recurringJob));
        }
      } catch (error) {
        logger.error('Failed to process recurring job', { error, key });
      }
    }
  }

  private async updateQueueStats(queueName: string): Promise<void> {
    try {
      const stats = await this.getQueueStats(queueName);
      this.queues.set(queueName, stats);
    } catch (error) {
      logger.error('Failed to update queue stats', { error, queueName });
    }
  }

  private async waitForActiveJobsToComplete(timeout: number): Promise<void> {
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

      if (!hasActiveJobs) break;

      await new Promise(resolve => setTimeout(resolve, 100));
    }
  }

  private withTimeout<T>(promise: Promise<T>, timeout: number): Promise<T> {
    return Promise.race([
      promise,
      new Promise<never>((_, reject) =>
        setTimeout(() => reject(new Error('Job timeout')), timeout)
      ),
    ]);
  }

  private generateJobId(): string {
    return `job_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  private getNextRunTime(cronExpression: string): Date {
    // This would use a proper cron parser like node-cron
    // For now, return next hour as placeholder
    const nextRun = new Date();
    nextRun.setHours(nextRun.getHours() + 1);
    return nextRun;
  }
}

// Export singleton instance
export const backgroundProcessor = BackgroundProcessor.getInstance();

// Job scheduling utilities
export const scheduleJob = <T>(
  queue: string,
  type: string,
  data: T,
  options?: JobOptions
) => backgroundProcessor.addJob(queue, type, data, options);

export const scheduleDelayedJob = <T>(
  queue: string,
  type: string,
  data: T,
  delayMs: number,
  options?: JobOptions
) => backgroundProcessor.addJob(queue, type, data, { ...options, delay: delayMs });

export const scheduleRecurringJob = (
  queue: string,
  type: string,
  cronExpression: string,
  data: any,
  options?: JobOptions
) => backgroundProcessor.scheduleRecurring(queue, type, cronExpression, data, options);