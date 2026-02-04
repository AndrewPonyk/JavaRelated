export interface JobOptions {
    priority?: number;
    delay?: number;
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
export declare class BackgroundProcessor {
    private static instance;
    private handlers;
    private queues;
    private isRunning;
    private workers;
    private concurrency;
    private constructor();
    static getInstance(): BackgroundProcessor;
    addJob<T>(queue: string, type: string, data: T, options?: JobOptions): Promise<string>;
    registerHandler<T>(type: string, handler: JobHandler<T>): void;
    start(): Promise<void>;
    stop(): Promise<void>;
    getQueueStats(queueName: string): Promise<QueueStats>;
    getJob(queue: string, jobId: string): Promise<Job | null>;
    scheduleRecurring(queue: string, type: string, cronExpression: string, data: any, options?: JobOptions): Promise<void>;
    getPerformanceMetrics(): {
        totalJobs: number;
        activeJobs: number;
        completedJobs: number;
        failedJobs: number;
        averageProcessingTime: number;
        throughput: number;
    };
    private setupDefaultHandlers;
    private initializeQueues;
    private startWorker;
    private processNextJob;
    private executeJob;
    private moveToCompleted;
    private moveToFailed;
    private scheduleRetry;
    private startDelayedJobProcessor;
    private moveDelayedJobsToWaiting;
    private startCleanupTask;
    private cleanupOldJobs;
    private processRecurringJobs;
    private updateQueueStats;
    private waitForActiveJobsToComplete;
    private withTimeout;
    private generateJobId;
    private getNextRunTime;
}
export declare const backgroundProcessor: BackgroundProcessor;
export declare const scheduleJob: <T>(queue: string, type: string, data: T, options?: JobOptions) => Promise<string>;
export declare const scheduleDelayedJob: <T>(queue: string, type: string, data: T, delayMs: number, options?: JobOptions) => Promise<string>;
export declare const scheduleRecurringJob: (queue: string, type: string, cronExpression: string, data: any, options?: JobOptions) => Promise<void>;
//# sourceMappingURL=backgroundProcessor.d.ts.map