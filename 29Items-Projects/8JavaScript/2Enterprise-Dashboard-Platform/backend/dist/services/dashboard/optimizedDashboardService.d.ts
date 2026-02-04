import { DashboardService } from './dashboardService.js';
export declare class OptimizedDashboardService extends DashboardService {
    getOptimizedUserDashboards(userId: string, query: any): Promise<any>;
    createOptimizedDashboard(data: any): Promise<any>;
    batchCreateDashboards(dashboards: any[], userId: string): Promise<any[]>;
    getOptimizedDashboardAnalytics(dashboardId: string, userId: string, query: any): Promise<any>;
    searchOptimizedDashboards(userId: string, searchQuery: string, options?: any): Promise<any>;
    warmDashboardCaches(dashboardIds: string[]): Promise<void>;
    private getCursorPaginatedDashboards;
    private executeOptimizedDashboardQuery;
    private buildDashboardWhereClause;
    private buildSearchWhereClause;
    private prewarmRelatedCaches;
    private createQueryHash;
}
export declare const optimizedDashboardService: OptimizedDashboardService;
//# sourceMappingURL=optimizedDashboardService.d.ts.map