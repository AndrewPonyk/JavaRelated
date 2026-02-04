export interface QueryMetrics {
    query: string;
    duration: number;
    rows: number;
    cached: boolean;
    timestamp: Date;
}
export interface PerformanceConfig {
    enableQueryLogging: boolean;
    slowQueryThreshold: number;
    batchSize: number;
    connectionPoolSize: number;
    queryTimeout: number;
    enableQueryOptimization: boolean;
}
export declare class DatabaseOptimizer {
    private static instance;
    private queryMetrics;
    private slowQueries;
    private readonly config;
    private constructor();
    static getInstance(): DatabaseOptimizer;
    private setupPerformanceMonitoring;
    paginateWithCursor<T>(model: any, options: {
        cursor?: string;
        take: number;
        where?: any;
        orderBy?: any;
        include?: any;
        select?: any;
    }): Promise<{
        data: T[];
        nextCursor?: string;
        hasMore: boolean;
        total?: number;
    }>;
    batchUpsert<T>(model: any, records: T[], options: {
        uniqueField: string;
        batchSize?: number;
        skipDuplicates?: boolean;
    }): Promise<T[]>;
    performAggregation(query: string, params?: any[]): Promise<any[]>;
    performFullTextSearch<T>(model: any, searchQuery: string, options: {
        fields: string[];
        limit?: number;
        offset?: number;
        where?: any;
        include?: any;
    }): Promise<{
        data: T[];
        total: number;
    }>;
    optimizeConnectionPool(): Promise<void>;
    getQueryPerformanceReport(): {
        totalQueries: number;
        averageDuration: number;
        slowQueries: Array<{
            query: string;
            count: number;
            avgDuration: number;
        }>;
        recentMetrics: QueryMetrics[];
    };
    performHealthCheck(): Promise<{
        status: 'healthy' | 'degraded' | 'unhealthy';
        metrics: {
            connectionTime: number;
            queryTime: number;
            connectionCount: number;
            poolUtilization: number;
        };
        recommendations: string[];
    }>;
    private cleanupMetrics;
    private recordQueryMetrics;
    private addQueryHints;
    private getConnectionStats;
}
export declare const databaseOptimizer: DatabaseOptimizer;
export declare const optimizeQuery: <T>(operation: () => Promise<T>, queryName: string) => Promise<T>;
export declare const QueryBuilders: {
    buildFullTextWhere: (fields: string[], searchTerm: string, additionalWhere?: any) => any;
    buildPaginationQuery: (page: number, limit: number) => {
        skip: number;
        take: number;
    };
    buildOptimizedInclude: (relations: string[]) => any;
};
//# sourceMappingURL=databaseOptimizer.d.ts.map