import DataLoader from 'dataloader';
import type { User, Dashboard, Widget } from '@prisma/client';
export declare const createUserLoader: () => DataLoader<string, any, string>;
export declare const createDashboardLoader: () => DataLoader<string, any, string>;
export declare const createWidgetLoader: () => DataLoader<string, any, string>;
export declare const createDashboardWidgetsLoader: () => DataLoader<string, Widget[], string>;
export declare const createUserDashboardsLoader: () => DataLoader<string, Dashboard[], string>;
export declare const createAnalyticsLoader: () => DataLoader<string, any[], string>;
export declare class DataLoaderContext {
    readonly userLoader: DataLoader<string, User | null>;
    readonly dashboardLoader: DataLoader<string, Dashboard | null>;
    readonly widgetLoader: DataLoader<string, Widget | null>;
    readonly dashboardWidgetsLoader: DataLoader<string, Widget[]>;
    readonly userDashboardsLoader: DataLoader<string, Dashboard[]>;
    readonly analyticsLoader: DataLoader<string, any[]>;
    constructor();
    clearAll(): void;
    clearUser(userId: string): void;
    clearDashboard(dashboardId: string): void;
    clearWidget(widgetId: string): void;
    primeUser(user: User): void;
    primeDashboard(dashboard: Dashboard): void;
    primeWidget(widget: Widget): void;
    getStats(): Record<string, any>;
}
export declare const createDataLoaderMiddleware: () => (req: any, res: any, next: any) => void;
declare global {
    namespace Express {
        interface Request {
            dataLoaders: DataLoaderContext;
        }
    }
}
export default DataLoaderContext;
//# sourceMappingURL=dataLoader.d.ts.map