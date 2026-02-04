import DataLoader from 'dataloader';
import { prisma } from '@/config/database.js';
import { logger } from './logger.js';
export const createUserLoader = () => {
    return new DataLoader(async (userIds) => {
        const users = await prisma.user.findMany({
            where: {
                id: { in: userIds },
            },
            include: {
                profile: true,
            },
        });
        const userMap = new Map(users.map(user => [user.id, user]));
        return userIds.map(id => userMap.get(id) || null);
    }, {
        cache: true,
        maxBatchSize: 100,
        batchScheduleFn: callback => setTimeout(callback, 10),
    });
};
export const createDashboardLoader = () => {
    return new DataLoader(async (dashboardIds) => {
        const dashboards = await prisma.dashboard.findMany({
            where: {
                id: { in: dashboardIds },
            },
            include: {
                user: {
                    include: { profile: true },
                },
                widgets: true,
            },
        });
        const dashboardMap = new Map(dashboards.map(dashboard => [dashboard.id, dashboard]));
        return dashboardIds.map(id => dashboardMap.get(id) || null);
    }, {
        cache: true,
        maxBatchSize: 50,
        batchScheduleFn: callback => setTimeout(callback, 10),
    });
};
export const createWidgetLoader = () => {
    return new DataLoader(async (widgetIds) => {
        const widgets = await prisma.widget.findMany({
            where: {
                id: { in: widgetIds },
            },
            include: {
                dashboard: true,
                user: true,
            },
        });
        const widgetMap = new Map(widgets.map(widget => [widget.id, widget]));
        return widgetIds.map(id => widgetMap.get(id) || null);
    }, {
        cache: true,
        maxBatchSize: 200,
        batchScheduleFn: callback => setTimeout(callback, 10),
    });
};
export const createDashboardWidgetsLoader = () => {
    return new DataLoader(async (dashboardIds) => {
        const widgets = await prisma.widget.findMany({
            where: {
                dashboardId: { in: dashboardIds },
            },
            include: {
                user: true,
            },
            orderBy: [
                { createdAt: 'asc' },
            ],
        });
        const widgetsByDashboard = new Map();
        for (const widget of widgets) {
            const dashboardId = widget.dashboardId;
            if (!widgetsByDashboard.has(dashboardId)) {
                widgetsByDashboard.set(dashboardId, []);
            }
            widgetsByDashboard.get(dashboardId).push(widget);
        }
        return dashboardIds.map(id => widgetsByDashboard.get(id) || []);
    }, {
        cache: true,
        maxBatchSize: 20,
        batchScheduleFn: callback => setTimeout(callback, 15),
    });
};
export const createUserDashboardsLoader = () => {
    return new DataLoader(async (userIds) => {
        const dashboards = await prisma.dashboard.findMany({
            where: {
                userId: { in: userIds },
            },
            include: {
                widgets: {
                    select: {
                        id: true,
                        title: true,
                        type: true,
                    },
                },
            },
            orderBy: [
                { updatedAt: 'desc' },
            ],
        });
        const dashboardsByUser = new Map();
        for (const dashboard of dashboards) {
            const userId = dashboard.userId;
            if (!dashboardsByUser.has(userId)) {
                dashboardsByUser.set(userId, []);
            }
            dashboardsByUser.get(userId).push(dashboard);
        }
        return userIds.map(id => dashboardsByUser.get(id) || []);
    }, {
        cache: true,
        maxBatchSize: 50,
        batchScheduleFn: callback => setTimeout(callback, 20),
    });
};
export const createAnalyticsLoader = () => {
    return new DataLoader(async (dashboardIds) => {
        const analytics = await prisma.dashboardAnalytics.findMany({
            where: {
                dashboardId: { in: dashboardIds },
                date: {
                    gte: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000),
                },
            },
            orderBy: {
                date: 'asc',
            },
        });
        const analyticsByDashboard = new Map();
        for (const analytic of analytics) {
            const dashboardId = analytic.dashboardId;
            if (!analyticsByDashboard.has(dashboardId)) {
                analyticsByDashboard.set(dashboardId, []);
            }
            analyticsByDashboard.get(dashboardId).push(analytic);
        }
        return dashboardIds.map(id => analyticsByDashboard.get(id) || []);
    }, {
        cache: true,
        maxBatchSize: 20,
        batchScheduleFn: callback => setTimeout(callback, 25),
    });
};
export class DataLoaderContext {
    userLoader;
    dashboardLoader;
    widgetLoader;
    dashboardWidgetsLoader;
    userDashboardsLoader;
    analyticsLoader;
    constructor() {
        this.userLoader = createUserLoader();
        this.dashboardLoader = createDashboardLoader();
        this.widgetLoader = createWidgetLoader();
        this.dashboardWidgetsLoader = createDashboardWidgetsLoader();
        this.userDashboardsLoader = createUserDashboardsLoader();
        this.analyticsLoader = createAnalyticsLoader();
    }
    clearAll() {
        this.userLoader.clearAll();
        this.dashboardLoader.clearAll();
        this.widgetLoader.clearAll();
        this.dashboardWidgetsLoader.clearAll();
        this.userDashboardsLoader.clearAll();
        this.analyticsLoader.clearAll();
    }
    clearUser(userId) {
        this.userLoader.clear(userId);
        this.userDashboardsLoader.clear(userId);
    }
    clearDashboard(dashboardId) {
        this.dashboardLoader.clear(dashboardId);
        this.dashboardWidgetsLoader.clear(dashboardId);
        this.analyticsLoader.clear(dashboardId);
    }
    clearWidget(widgetId) {
        this.widgetLoader.clear(widgetId);
    }
    primeUser(user) {
        this.userLoader.prime(user.id, user);
    }
    primeDashboard(dashboard) {
        this.dashboardLoader.prime(dashboard.id, dashboard);
    }
    primeWidget(widget) {
        this.widgetLoader.prime(widget.id, widget);
    }
    getStats() {
        return {
            userLoader: {
                cacheSize: this.userLoader.clearAll.length,
            },
            dashboardLoader: {
                cacheSize: this.dashboardLoader.clearAll.length,
            },
            widgetLoader: {
                cacheSize: this.widgetLoader.clearAll.length,
            },
        };
    }
}
export const createDataLoaderMiddleware = () => {
    return (req, res, next) => {
        req.dataLoaders = new DataLoaderContext();
        if (process.env.NODE_ENV === 'development') {
            const originalEnd = res.end;
            res.end = function (...args) {
                const stats = req.dataLoaders.getStats();
                logger.debug('DataLoader Stats', {
                    requestId: req.requestId,
                    path: req.path,
                    stats,
                });
                originalEnd.apply(this, args);
            };
        }
        next();
    };
};
export default DataLoaderContext;
//# sourceMappingURL=dataLoader.js.map