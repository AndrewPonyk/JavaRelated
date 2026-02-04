import DataLoader from 'dataloader';
import { prisma } from '@/config/database.js';
import type { User, Dashboard, Widget } from '@prisma/client';
import { logger } from './logger.js';

// User DataLoader
export const createUserLoader = () => {
  return new DataLoader<string, User | null>(
    async (userIds: readonly string[]) => {
      const users = await prisma.user.findMany({
        where: {
          id: { in: userIds as string[] },
        },
        include: {
          profile: true,
        },
      });

      // Create a map for O(1) lookups
      const userMap = new Map(users.map((user: User) => [user.id, user]));

      // Return users in the same order as requested
      return userIds.map((id: string) => userMap.get(id) || null);
    },
    {
      cache: true,
      maxBatchSize: 100,
      batchScheduleFn: (callback: () => void) => setTimeout(callback, 10), // 10ms batching window
    }
  );
};

// Dashboard DataLoader
export const createDashboardLoader = () => {
  return new DataLoader<string, Dashboard | null>(
    async (dashboardIds: readonly string[]) => {
      const dashboards = await prisma.dashboard.findMany({
        where: {
          id: { in: dashboardIds as string[] },
        },
        include: {
          user: {
            include: { profile: true },
          },
          widgets: true,
        },
      });

      const dashboardMap = new Map(dashboards.map((dashboard: Dashboard) => [dashboard.id, dashboard]));
      return dashboardIds.map((id: string) => dashboardMap.get(id) || null);
    },
    {
      cache: true,
      maxBatchSize: 50,
      batchScheduleFn: (callback: () => void) => setTimeout(callback, 10),
    }
  );
};

// Widget DataLoader
export const createWidgetLoader = () => {
  return new DataLoader<string, Widget | null>(
    async (widgetIds: readonly string[]) => {
      const widgets = await prisma.widget.findMany({
        where: {
          id: { in: widgetIds as string[] },
        },
        include: {
          dashboard: true,
          user: true,
        },
      });

      const widgetMap = new Map(widgets.map((widget: Widget) => [widget.id, widget]));
      return widgetIds.map((id: string) => widgetMap.get(id) || null);
    },
    {
      cache: true,
      maxBatchSize: 200,
      batchScheduleFn: (callback: () => void) => setTimeout(callback, 10),
    }
  );
};

// Dashboard Widgets DataLoader (for loading widgets by dashboard ID)
export const createDashboardWidgetsLoader = () => {
  return new DataLoader<string, Widget[]>(
    async (dashboardIds: readonly string[]) => {
      const widgets = await prisma.widget.findMany({
        where: {
          dashboardId: { in: dashboardIds as string[] },
        },
        include: {
          user: true,
        },
        orderBy: [
          { createdAt: 'asc' },
        ],
      });

      // Group widgets by dashboard ID
      const widgetsByDashboard = new Map<string, Widget[]>();

      for (const widget of widgets) {
        const dashboardId = widget.dashboardId;
        if (!widgetsByDashboard.has(dashboardId)) {
          widgetsByDashboard.set(dashboardId, []);
        }
        widgetsByDashboard.get(dashboardId)!.push(widget);
      }

      // Return widgets in the same order as requested dashboard IDs
      return dashboardIds.map((id: string) => widgetsByDashboard.get(id) || []);
    },
    {
      cache: true,
      maxBatchSize: 20,
      batchScheduleFn: (callback: () => void) => setTimeout(callback, 15),
    }
  );
};

// User Dashboards DataLoader
export const createUserDashboardsLoader = () => {
  return new DataLoader<string, Dashboard[]>(
    async (userIds: readonly string[]) => {
      const dashboards = await prisma.dashboard.findMany({
        where: {
          userId: { in: userIds as string[] },
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

      // Group dashboards by user ID
      const dashboardsByUser = new Map<string, Dashboard[]>();

      for (const dashboard of dashboards) {
        const userId = dashboard.userId;
        if (!dashboardsByUser.has(userId)) {
          dashboardsByUser.set(userId, []);
        }
        dashboardsByUser.get(userId)!.push(dashboard);
      }

      return userIds.map((id: string) => dashboardsByUser.get(id) || []);
    },
    {
      cache: true,
      maxBatchSize: 50,
      batchScheduleFn: (callback: () => void) => setTimeout(callback, 20),
    }
  );
};

// Analytics DataLoader (for loading dashboard analytics)
export const createAnalyticsLoader = () => {
  return new DataLoader<string, any[]>(
    async (dashboardIds: readonly string[]) => {
      const analytics = await prisma.dashboardAnalytics.findMany({
        where: {
          dashboardId: { in: dashboardIds as string[] },
          date: {
            gte: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000), // Last 30 days
          },
        },
        orderBy: {
          date: 'asc',
        },
      });

      // Group analytics by dashboard ID
      const analyticsByDashboard = new Map<string, any[]>();

      for (const analytic of analytics) {
        const dashboardId = analytic.dashboardId;
        if (!analyticsByDashboard.has(dashboardId)) {
          analyticsByDashboard.set(dashboardId, []);
        }
        analyticsByDashboard.get(dashboardId)!.push(analytic);
      }

      return dashboardIds.map((id: string) => analyticsByDashboard.get(id) || []);
    },
    {
      cache: true,
      maxBatchSize: 20,
      batchScheduleFn: (callback: () => void) => setTimeout(callback, 25),
    }
  );
};

// DataLoader Context - holds all loaders for a request
export class DataLoaderContext {
  public readonly userLoader: DataLoader<string, User | null>;
  public readonly dashboardLoader: DataLoader<string, Dashboard | null>;
  public readonly widgetLoader: DataLoader<string, Widget | null>;
  public readonly dashboardWidgetsLoader: DataLoader<string, Widget[]>;
  public readonly userDashboardsLoader: DataLoader<string, Dashboard[]>;
  public readonly analyticsLoader: DataLoader<string, any[]>;

  constructor() {
    this.userLoader = createUserLoader();
    this.dashboardLoader = createDashboardLoader();
    this.widgetLoader = createWidgetLoader();
    this.dashboardWidgetsLoader = createDashboardWidgetsLoader();
    this.userDashboardsLoader = createUserDashboardsLoader();
    this.analyticsLoader = createAnalyticsLoader();
  }

  // Clear all caches (useful for mutations)
  public clearAll(): void {
    this.userLoader.clearAll();
    this.dashboardLoader.clearAll();
    this.widgetLoader.clearAll();
    this.dashboardWidgetsLoader.clearAll();
    this.userDashboardsLoader.clearAll();
    this.analyticsLoader.clearAll();
  }

  // Clear specific caches
  public clearUser(userId: string): void {
    this.userLoader.clear(userId);
    this.userDashboardsLoader.clear(userId);
  }

  public clearDashboard(dashboardId: string): void {
    this.dashboardLoader.clear(dashboardId);
    this.dashboardWidgetsLoader.clear(dashboardId);
    this.analyticsLoader.clear(dashboardId);
  }

  public clearWidget(widgetId: string): void {
    this.widgetLoader.clear(widgetId);
  }

  // Prime caches with data
  public primeUser(user: User): void {
    this.userLoader.prime(user.id, user);
  }

  public primeDashboard(dashboard: Dashboard): void {
    this.dashboardLoader.prime(dashboard.id, dashboard);
  }

  public primeWidget(widget: Widget): void {
    this.widgetLoader.prime(widget.id, widget);
  }

  // Get loader stats for monitoring
  public getStats(): Record<string, any> {
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
      // Add other loader stats as needed
    };
  }
}

// Middleware to attach DataLoader context to requests
export const createDataLoaderMiddleware = () => {
  return (req: any, res: any, next: any) => {
    // Create new DataLoader context for each request
    req.dataLoaders = new DataLoaderContext();

    // Log DataLoader performance in development
    if (process.env.NODE_ENV === 'development') {
      const originalEnd = res.end;
      res.end = function(...args: any[]) {
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

// Extend Express Request interface
declare global {
  namespace Express {
    interface Request {
      dataLoaders: DataLoaderContext;
    }
  }
}

export default DataLoaderContext;