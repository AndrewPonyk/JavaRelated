import type { Dashboard, Widget, User, DashboardShare, DashboardAnalytics, SharePermission } from '@prisma/client';
type DashboardWithRelations = Dashboard & {
    user?: User;
    widgets?: Widget[];
    shares?: DashboardShare[];
    analytics?: DashboardAnalytics[];
};
interface DashboardQuery {
    page: number;
    limit: number;
    search?: string;
    sortBy: 'createdAt' | 'updatedAt' | 'title';
    sortOrder: 'asc' | 'desc';
    isPublic?: boolean;
    isTemplate?: boolean;
    tags?: string[];
}
interface PaginatedResult<T> {
    data: T[];
    total: number;
    page: number;
    limit: number;
    pages: number;
    hasNext: boolean;
    hasPrev: boolean;
}
interface CreateDashboardData {
    title: string;
    description?: string;
    isPublic?: boolean;
    layout?: any[];
    settings?: any;
    userId: string;
}
interface UpdateDashboardData {
    title?: string;
    description?: string;
    isPublic?: boolean;
    layout?: any[];
    settings?: any;
}
interface AnalyticsQuery {
    startDate?: string;
    endDate?: string;
    granularity: 'hour' | 'day' | 'week' | 'month';
}
export declare class DashboardService {
    getUserDashboards(userId: string, query: DashboardQuery): Promise<PaginatedResult<DashboardWithRelations>>;
    getDashboard(dashboardId: string, userId: string): Promise<DashboardWithRelations | null>;
    createDashboard(data: CreateDashboardData): Promise<DashboardWithRelations>;
    updateDashboard(dashboardId: string, userId: string, data: UpdateDashboardData): Promise<DashboardWithRelations | null>;
    deleteDashboard(dashboardId: string, userId: string): Promise<boolean>;
    shareDashboard(dashboardId: string, userId: string, emails: string[], permission?: SharePermission): Promise<{
        shared: number;
        errors: string[];
    }>;
    getDashboardAnalytics(dashboardId: string, userId: string, query: AnalyticsQuery): Promise<any>;
    private generateSlug;
    private checkSlugExists;
    private checkDashboardPermissions;
    private checkPermissionLevel;
    private recordDashboardView;
    private logActivity;
    private createQueryHash;
}
export declare const dashboardService: DashboardService;
export {};
//# sourceMappingURL=dashboardService.d.ts.map