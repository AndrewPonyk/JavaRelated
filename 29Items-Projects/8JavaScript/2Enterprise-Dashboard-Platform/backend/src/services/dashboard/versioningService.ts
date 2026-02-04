import { prisma } from '@/config/database.js';
import { extendedCacheService as cacheService } from '@/services/cache/cacheService.js';
import { logger, logBusinessEvent } from '@/utils/logger.js';
import { NotFoundError, ValidationError, AuthorizationError, DatabaseError } from '@/utils/errors.js';
import crypto from 'crypto';

// Version types
export interface DashboardVersion {
  id: string;
  dashboardId: string;
  versionNumber: number;
  title: string;
  description?: string;
  layout: any;
  settings: any;
  widgets: any[];
  createdBy: string;
  createdAt: Date;
  changeDescription?: string;
  snapshot: any;
}

// In-memory version storage (in production, use a dedicated table)
const dashboardVersions = new Map<string, DashboardVersion[]>();

export class VersioningService {
  private static instance: VersioningService;
  private readonly MAX_VERSIONS_PER_DASHBOARD = 50;

  private constructor() {}

  static getInstance(): VersioningService {
    if (!VersioningService.instance) {
      VersioningService.instance = new VersioningService();
    }
    return VersioningService.instance;
  }

  /**
   * Create a new version snapshot of a dashboard
   */
  async createVersion(
    dashboardId: string,
    userId: string,
    changeDescription?: string
  ): Promise<DashboardVersion> {
    // Get current dashboard state
    const dashboard = await prisma.dashboard.findFirst({
      where: {
        id: dashboardId,
        OR: [
          { userId },
          { dashboardShares: { some: { userId, permission: { in: ['WRITE', 'ADMIN'] } } } }
        ]
      },
      include: {
        widgets: {
          select: {
            id: true,
            title: true,
            type: true,
            config: true,
            position: true,
            dataSource: true,
            query: true,
            refreshRate: true
          }
        },
        user: {
          select: { id: true, email: true, firstName: true, lastName: true }
        }
      }
    });

    if (!dashboard) {
      throw new NotFoundError('Dashboard');
    }

    // Get existing versions
    let versions = dashboardVersions.get(dashboardId) || [];

    // Determine next version number
    const nextVersionNumber = versions.length > 0
      ? Math.max(...versions.map(v => v.versionNumber)) + 1
      : 1;

    // Create version snapshot
    const version: DashboardVersion = {
      id: `version-${crypto.randomBytes(8).toString('hex')}`,
      dashboardId,
      versionNumber: nextVersionNumber,
      title: dashboard.title,
      description: dashboard.description || undefined,
      layout: dashboard.layout,
      settings: dashboard.settings,
      widgets: dashboard.widgets,
      createdBy: userId,
      createdAt: new Date(),
      changeDescription,
      snapshot: {
        dashboardId: dashboard.id,
        title: dashboard.title,
        description: dashboard.description,
        isPublic: dashboard.isPublic,
        isTemplate: dashboard.isTemplate,
        layout: dashboard.layout,
        settings: dashboard.settings,
        widgets: dashboard.widgets,
        updatedAt: dashboard.updatedAt
      }
    };

    // Add version to list
    versions.push(version);

    // Trim to max versions (keep most recent)
    if (versions.length > this.MAX_VERSIONS_PER_DASHBOARD) {
      versions = versions.slice(-this.MAX_VERSIONS_PER_DASHBOARD);
    }

    // Store versions
    dashboardVersions.set(dashboardId, versions);

    // Log activity
    await prisma.activityLog.create({
      data: {
        action: 'DASHBOARD_VERSION_CREATED',
        entity: 'dashboard',
        entityId: dashboardId,
        userId,
        details: JSON.stringify({
          versionId: version.id,
          versionNumber: version.versionNumber,
          changeDescription
        })
      }
    });

    logBusinessEvent('DASHBOARD_VERSION_CREATED', {
      dashboardId,
      userId,
      versionId: version.id,
      versionNumber: version.versionNumber
    });

    return version;
  }

  /**
   * Get all versions for a dashboard
   */
  async getVersions(
    dashboardId: string,
    userId: string,
    options?: { page?: number; limit?: number }
  ): Promise<{ versions: DashboardVersion[]; total: number }> {
    // Verify user has access
    const dashboard = await this.verifyDashboardAccess(dashboardId, userId, 'READ');
    if (!dashboard) {
      throw new NotFoundError('Dashboard');
    }

    const versions = dashboardVersions.get(dashboardId) || [];
    const page = options?.page || 1;
    const limit = options?.limit || 20;

    // Sort by version number descending (newest first)
    const sortedVersions = [...versions].sort((a, b) => b.versionNumber - a.versionNumber);

    // Paginate
    const start = (page - 1) * limit;
    const paginatedVersions = sortedVersions.slice(start, start + limit);

    return {
      versions: paginatedVersions,
      total: versions.length
    };
  }

  /**
   * Get a specific version
   */
  async getVersion(
    dashboardId: string,
    versionId: string,
    userId: string
  ): Promise<DashboardVersion | null> {
    // Verify user has access
    const dashboard = await this.verifyDashboardAccess(dashboardId, userId, 'READ');
    if (!dashboard) {
      throw new NotFoundError('Dashboard');
    }

    const versions = dashboardVersions.get(dashboardId) || [];
    return versions.find(v => v.id === versionId) || null;
  }

  /**
   * Restore dashboard to a previous version
   */
  async restoreVersion(
    dashboardId: string,
    versionId: string,
    userId: string
  ): Promise<any> {
    // Verify user has write access
    const dashboard = await this.verifyDashboardAccess(dashboardId, userId, 'WRITE');
    if (!dashboard) {
      throw new NotFoundError('Dashboard');
    }

    // Get the version to restore
    const versions = dashboardVersions.get(dashboardId) || [];
    const version = versions.find(v => v.id === versionId);

    if (!version) {
      throw new NotFoundError('Version');
    }

    // Create a backup of current state before restoring
    await this.createVersion(
      dashboardId,
      userId,
      `Auto-backup before restoring to version ${version.versionNumber}`
    );

    // Restore dashboard to version state
    const restoredDashboard = await prisma.$transaction(async (tx) => {
      // Update dashboard
      const updated = await tx.dashboard.update({
        where: { id: dashboardId },
        data: {
          title: version.title,
          description: version.description,
          layout: version.layout as any,
          settings: version.settings as any
        }
      });

      // Delete current widgets
      await tx.widget.deleteMany({
        where: { dashboardId }
      });

      // Restore widgets from version
      if (version.widgets && version.widgets.length > 0) {
        for (const widget of version.widgets) {
          await tx.widget.create({
            data: {
              id: crypto.randomUUID(),
              title: widget.title,
              type: widget.type,
              config: widget.config || {},
              position: widget.position || {},
              dataSource: widget.dataSource,
              query: widget.query,
              refreshRate: widget.refreshRate,
              dashboardId,
              userId
            }
          });
        }
      }

      return updated;
    });

    // Invalidate caches
    await Promise.all([
      cacheService.invalidateDashboard(dashboardId),
      cacheService.invalidateUserDashboards(dashboard.userId)
    ]);

    // Log activity
    await prisma.activityLog.create({
      data: {
        action: 'DASHBOARD_VERSION_RESTORED',
        entity: 'dashboard',
        entityId: dashboardId,
        userId,
        details: JSON.stringify({
          restoredVersionId: versionId,
          restoredVersionNumber: version.versionNumber
        })
      }
    });

    logBusinessEvent('DASHBOARD_VERSION_RESTORED', {
      dashboardId,
      userId,
      versionId,
      versionNumber: version.versionNumber
    });

    // Fetch and return the restored dashboard with relations
    const fullDashboard = await prisma.dashboard.findUnique({
      where: { id: dashboardId },
      include: {
        widgets: true,
        user: {
          select: { id: true, email: true, firstName: true, lastName: true }
        }
      }
    });

    return fullDashboard;
  }

  /**
   * Compare two versions
   */
  async compareVersions(
    dashboardId: string,
    versionId1: string,
    versionId2: string,
    userId: string
  ): Promise<VersionDiff> {
    // Verify user has access
    const dashboard = await this.verifyDashboardAccess(dashboardId, userId, 'READ');
    if (!dashboard) {
      throw new NotFoundError('Dashboard');
    }

    const versions = dashboardVersions.get(dashboardId) || [];
    const version1 = versions.find(v => v.id === versionId1);
    const version2 = versions.find(v => v.id === versionId2);

    if (!version1 || !version2) {
      throw new NotFoundError('Version');
    }

    return {
      version1: {
        id: version1.id,
        versionNumber: version1.versionNumber,
        createdAt: version1.createdAt
      },
      version2: {
        id: version2.id,
        versionNumber: version2.versionNumber,
        createdAt: version2.createdAt
      },
      changes: {
        title: version1.title !== version2.title,
        description: version1.description !== version2.description,
        layout: JSON.stringify(version1.layout) !== JSON.stringify(version2.layout),
        settings: JSON.stringify(version1.settings) !== JSON.stringify(version2.settings),
        widgetCount: {
          before: version1.widgets.length,
          after: version2.widgets.length
        }
      }
    };
  }

  /**
   * Delete a specific version
   */
  async deleteVersion(
    dashboardId: string,
    versionId: string,
    userId: string
  ): Promise<boolean> {
    // Verify user has admin access
    const dashboard = await this.verifyDashboardAccess(dashboardId, userId, 'ADMIN');
    if (!dashboard) {
      throw new AuthorizationError('Insufficient permissions to delete version');
    }

    const versions = dashboardVersions.get(dashboardId) || [];
    const versionIndex = versions.findIndex(v => v.id === versionId);

    if (versionIndex === -1) {
      return false;
    }

    versions.splice(versionIndex, 1);
    dashboardVersions.set(dashboardId, versions);

    // Log activity
    await prisma.activityLog.create({
      data: {
        action: 'DASHBOARD_VERSION_DELETED',
        entity: 'dashboard',
        entityId: dashboardId,
        userId,
        details: JSON.stringify({ versionId })
      }
    });

    return true;
  }

  /**
   * Auto-save version on significant changes
   */
  async autoSaveVersion(dashboardId: string, userId: string): Promise<void> {
    const versions = dashboardVersions.get(dashboardId) || [];

    // Only auto-save if last version is older than 5 minutes
    const lastVersion = versions[versions.length - 1];
    if (lastVersion) {
      const fiveMinutesAgo = new Date(Date.now() - 5 * 60 * 1000);
      if (lastVersion.createdAt > fiveMinutesAgo) {
        return; // Too recent, skip auto-save
      }
    }

    await this.createVersion(dashboardId, userId, 'Auto-save');
  }

  /**
   * Verify dashboard access
   */
  private async verifyDashboardAccess(
    dashboardId: string,
    userId: string,
    requiredPermission: 'READ' | 'WRITE' | 'ADMIN'
  ): Promise<any> {
    const dashboard = await prisma.dashboard.findFirst({
      where: {
        id: dashboardId,
        OR: [
          { userId },
          { isPublic: requiredPermission === 'READ' ? true : undefined },
          {
            dashboardShares: {
              some: {
                userId,
                permission: this.getPermissionFilter(requiredPermission)
              }
            }
          }
        ]
      },
      include: {
        user: {
          select: { id: true, role: true }
        }
      }
    });

    if (!dashboard) return null;

    // Owner has all permissions
    if (dashboard.userId === userId) return dashboard;

    // Admins have all permissions
    if (['SUPER_ADMIN', 'ADMIN'].includes(dashboard.user.role)) {
      return dashboard;
    }

    return dashboard;
  }

  private getPermissionFilter(required: 'READ' | 'WRITE' | 'ADMIN'): any {
    switch (required) {
      case 'READ':
        return { in: ['READ', 'WRITE', 'ADMIN'] };
      case 'WRITE':
        return { in: ['WRITE', 'ADMIN'] };
      case 'ADMIN':
        return 'ADMIN';
      default:
        return { in: ['READ', 'WRITE', 'ADMIN'] };
    }
  }

  /**
   * Cleanup old versions for all dashboards
   */
  async cleanupOldVersions(maxAge: number = 90 * 24 * 60 * 60 * 1000): Promise<number> {
    const cutoffDate = new Date(Date.now() - maxAge);
    let cleaned = 0;

    for (const [dashboardId, versions] of dashboardVersions.entries()) {
      const filteredVersions = versions.filter(v => v.createdAt > cutoffDate);
      if (filteredVersions.length < versions.length) {
        cleaned += versions.length - filteredVersions.length;
        dashboardVersions.set(dashboardId, filteredVersions);
      }
    }

    if (cleaned > 0) {
      logger.info('Cleaned up old dashboard versions', { count: cleaned });
    }

    return cleaned;
  }
}

interface VersionDiff {
  version1: {
    id: string;
    versionNumber: number;
    createdAt: Date;
  };
  version2: {
    id: string;
    versionNumber: number;
    createdAt: Date;
  };
  changes: {
    title: boolean;
    description: boolean;
    layout: boolean;
    settings: boolean;
    widgetCount: {
      before: number;
      after: number;
    };
  };
}

// Export singleton instance
export const versioningService = VersioningService.getInstance();
