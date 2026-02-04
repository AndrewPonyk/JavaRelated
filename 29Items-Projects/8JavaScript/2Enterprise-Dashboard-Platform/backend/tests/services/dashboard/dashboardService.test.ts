import { DashboardService } from '@/services/dashboard/dashboardService.js';
import { mockPrisma, mockUser, mockDashboard, mockWidget } from '../../setup.js';

describe('DashboardService', () => {
  let dashboardService: DashboardService;

  beforeEach(() => {
    dashboardService = new DashboardService();
    clearAllMocks();
  });

  describe('getUserDashboards', () => {
    it('should return user dashboards with pagination', async () => {
      const mockDashboards = [mockDashboard];
      mockPrisma.dashboard.findMany.mockResolvedValue(mockDashboards);
      mockPrisma.dashboard.count.mockResolvedValue(1);

      const result = await dashboardService.getUserDashboards(mockUser.id, {
        page: 1,
        limit: 10,
        sortBy: 'updatedAt',
        sortOrder: 'desc'
      });

      expect(result).toEqual({
        data: mockDashboards,
        total: 1,
        page: 1,
        limit: 10,
        pages: 1,
        hasNext: false,
        hasPrev: false
      });
      expect(mockPrisma.dashboard.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            OR: expect.arrayContaining([
              { userId: mockUser.id },
              { isPublic: true, isTemplate: false },
              { dashboardShares: { some: { userId: mockUser.id } } }
            ])
          }),
          skip: 0,
          take: 10,
          orderBy: { updatedAt: 'desc' }
        })
      );
    });

    it('should filter dashboards by search query', async () => {
      const mockDashboards = [mockDashboard];
      mockPrisma.dashboard.findMany.mockResolvedValue(mockDashboards);
      mockPrisma.dashboard.count.mockResolvedValue(1);

      await dashboardService.getUserDashboards(mockUser.id, {
        page: 1,
        limit: 10,
        search: 'test'
      });

      expect(mockPrisma.dashboard.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            AND: [{
              OR: [
                { title: { contains: 'test', mode: 'insensitive' } },
                { description: { contains: 'test', mode: 'insensitive' } }
              ]
            }]
          })
        })
      );
    });

    it('should handle empty results', async () => {
      mockPrisma.dashboard.findMany.mockResolvedValue([]);
      mockPrisma.dashboard.count.mockResolvedValue(0);

      const result = await dashboardService.getUserDashboards(mockUser.id, {
        page: 1,
        limit: 10
      });

      expect(result).toEqual({
        data: [],
        total: 0,
        page: 1,
        limit: 10,
        pages: 0,
        hasNext: false,
        hasPrev: false
      });
    });

    it('should handle database errors', async () => {
      const dbError = new Error('Database connection failed');
      mockPrisma.dashboard.findMany.mockRejectedValue(dbError);

      await expect(
        dashboardService.getUserDashboards(mockUser.id, { page: 1, limit: 10 })
      ).rejects.toThrow('Database connection failed');
    });
  });

  describe('getDashboard', () => {
    it('should return dashboard by id for owner', async () => {
      mockPrisma.dashboard.findFirst.mockResolvedValue(mockDashboard);

      const result = await dashboardService.getDashboard(mockDashboard.id, mockUser.id);

      expect(result).toEqual(mockDashboard);
      expect(mockPrisma.dashboard.findFirst).toHaveBeenCalledWith({
        where: {
          id: mockDashboard.id,
          OR: [
            { userId: mockUser.id },
            { isPublic: true },
            { dashboardShares: { some: { userId: mockUser.id } } }
          ]
        },
        include: expect.objectContaining({
          user: true,
          widgets: true,
          dashboardShares: true
        })
      });
    });

    it('should return null for non-existent dashboard', async () => {
      mockPrisma.dashboard.findFirst.mockResolvedValue(null);

      const result = await dashboardService.getDashboard('non-existent', mockUser.id);

      expect(result).toBeNull();
    });

    it('should return public dashboard to any user', async () => {
      const publicDashboard = { ...mockDashboard, isPublic: true };
      mockPrisma.dashboard.findFirst.mockResolvedValue(publicDashboard);

      const result = await dashboardService.getDashboard(publicDashboard.id, 'other-user-id');

      expect(result).toEqual(publicDashboard);
    });
  });

  describe('createDashboard', () => {
    const createData = {
      title: 'New Dashboard',
      description: 'Test description',
      isPublic: false,
      userId: mockUser.id
    };

    it('should create dashboard with valid data', async () => {
      const newDashboard = { ...mockDashboard, ...createData };
      mockPrisma.dashboard.create.mockResolvedValue(newDashboard);

      const result = await dashboardService.createDashboard(createData);

      expect(result).toEqual(newDashboard);
      expect(mockPrisma.dashboard.create).toHaveBeenCalledWith({
        data: expect.objectContaining({
          title: createData.title,
          description: createData.description,
          isPublic: createData.isPublic,
          userId: createData.userId,
          slug: expect.any(String)
        }),
        include: expect.objectContaining({
          user: true,
          widgets: true
        })
      });
    });

    it('should generate unique slug from title', async () => {
      const newDashboard = { ...mockDashboard, ...createData };
      mockPrisma.dashboard.create.mockResolvedValue(newDashboard);

      await dashboardService.createDashboard(createData);

      expect(mockPrisma.dashboard.create).toHaveBeenCalledWith({
        data: expect.objectContaining({
          slug: expect.stringMatching(/^new-dashboard(-\d+)?$/)
        }),
        include: expect.any(Object)
      });
    });

    it('should handle creation errors', async () => {
      const dbError = new Error('Unique constraint violation');
      mockPrisma.dashboard.create.mockRejectedValue(dbError);

      await expect(
        dashboardService.createDashboard(createData)
      ).rejects.toThrow('Unique constraint violation');
    });
  });

  describe('updateDashboard', () => {
    const updateData = {
      title: 'Updated Dashboard',
      description: 'Updated description'
    };

    it('should update dashboard when user is owner', async () => {
      const updatedDashboard = { ...mockDashboard, ...updateData };
      mockPrisma.dashboard.findFirst.mockResolvedValue(mockDashboard);
      mockPrisma.dashboard.update.mockResolvedValue(updatedDashboard);

      const result = await dashboardService.updateDashboard(
        mockDashboard.id,
        mockUser.id,
        updateData
      );

      expect(result).toEqual(updatedDashboard);
      expect(mockPrisma.dashboard.update).toHaveBeenCalledWith({
        where: { id: mockDashboard.id },
        data: updateData,
        include: expect.objectContaining({
          user: true,
          widgets: true
        })
      });
    });

    it('should throw error when dashboard not found', async () => {
      mockPrisma.dashboard.findFirst.mockResolvedValue(null);

      await expect(
        dashboardService.updateDashboard('non-existent', mockUser.id, updateData)
      ).rejects.toThrow('Dashboard not found or access denied');
    });

    it('should throw error when user lacks permission', async () => {
      const otherUserDashboard = { ...mockDashboard, userId: 'other-user' };
      mockPrisma.dashboard.findFirst.mockResolvedValue(otherUserDashboard);

      await expect(
        dashboardService.updateDashboard(mockDashboard.id, mockUser.id, updateData)
      ).rejects.toThrow('Dashboard not found or access denied');
    });
  });

  describe('deleteDashboard', () => {
    it('should delete dashboard when user is owner', async () => {
      mockPrisma.dashboard.findFirst.mockResolvedValue(mockDashboard);
      mockPrisma.dashboard.delete.mockResolvedValue(mockDashboard);

      const result = await dashboardService.deleteDashboard(mockDashboard.id, mockUser.id);

      expect(result).toBe(true);
      expect(mockPrisma.dashboard.delete).toHaveBeenCalledWith({
        where: { id: mockDashboard.id }
      });
    });

    it('should return false when dashboard not found', async () => {
      mockPrisma.dashboard.findFirst.mockResolvedValue(null);

      const result = await dashboardService.deleteDashboard('non-existent', mockUser.id);

      expect(result).toBe(false);
      expect(mockPrisma.dashboard.delete).not.toHaveBeenCalled();
    });

    it('should handle deletion errors', async () => {
      mockPrisma.dashboard.findFirst.mockResolvedValue(mockDashboard);
      const dbError = new Error('Foreign key constraint violation');
      mockPrisma.dashboard.delete.mockRejectedValue(dbError);

      await expect(
        dashboardService.deleteDashboard(mockDashboard.id, mockUser.id)
      ).rejects.toThrow('Foreign key constraint violation');
    });
  });

  describe('shareDashboard', () => {
    const shareData = {
      email: 'shared@example.com',
      permission: 'read' as const
    };

    it('should share dashboard with valid user', async () => {
      const sharedUser = { ...mockUser, email: shareData.email };
      const shareRecord = {
        id: 'share_123',
        dashboardId: mockDashboard.id,
        userId: sharedUser.id,
        permission: shareData.permission,
        createdAt: new Date(),
        updatedAt: new Date()
      };

      mockPrisma.dashboard.findFirst.mockResolvedValue(mockDashboard);
      mockPrisma.user.findUnique.mockResolvedValue(sharedUser);
      mockPrisma.dashboardShare.upsert.mockResolvedValue(shareRecord);

      const result = await dashboardService.shareDashboard(
        mockDashboard.id,
        mockUser.id,
        shareData
      );

      expect(result).toEqual(shareRecord);
      expect(mockPrisma.dashboardShare.upsert).toHaveBeenCalledWith({
        where: {
          dashboardId_userId: {
            dashboardId: mockDashboard.id,
            userId: sharedUser.id
          }
        },
        create: expect.objectContaining({
          dashboardId: mockDashboard.id,
          userId: sharedUser.id,
          permission: shareData.permission
        }),
        update: {
          permission: shareData.permission
        }
      });
    });

    it('should throw error when sharing non-existent dashboard', async () => {
      mockPrisma.dashboard.findFirst.mockResolvedValue(null);

      await expect(
        dashboardService.shareDashboard('non-existent', mockUser.id, shareData)
      ).rejects.toThrow('Dashboard not found or access denied');
    });

    it('should throw error when sharing with non-existent user', async () => {
      mockPrisma.dashboard.findFirst.mockResolvedValue(mockDashboard);
      mockPrisma.user.findUnique.mockResolvedValue(null);

      await expect(
        dashboardService.shareDashboard(mockDashboard.id, mockUser.id, shareData)
      ).rejects.toThrow('User not found');
    });
  });

  describe('getDashboardAnalytics', () => {
    const analyticsQuery = {
      startDate: '2024-01-01',
      endDate: '2024-01-31',
      granularity: 'day'
    };

    it('should return analytics data for dashboard owner', async () => {
      const analyticsData = [
        {
          period: '2024-01-01',
          totalViews: 100,
          totalUniqueViews: 80,
          avgLoadTime: 1.5,
          avgBounceRate: 0.3
        }
      ];

      mockPrisma.dashboard.findFirst.mockResolvedValue(mockDashboard);
      mockPrisma.$queryRaw.mockResolvedValue(analyticsData);

      const result = await dashboardService.getDashboardAnalytics(
        mockDashboard.id,
        mockUser.id,
        analyticsQuery
      );

      expect(result).toEqual(analyticsData);
      expect(mockPrisma.$queryRaw).toHaveBeenCalledWith(
        expect.any(Object), // Prisma.sql template
        mockDashboard.id,
        expect.any(Date),
        expect.any(Date),
        'day'
      );
    });

    it('should throw error for unauthorized access', async () => {
      mockPrisma.dashboard.findFirst.mockResolvedValue(null);

      await expect(
        dashboardService.getDashboardAnalytics('non-existent', mockUser.id, analyticsQuery)
      ).rejects.toThrow('Dashboard not found or access denied');
    });

    it('should handle invalid date ranges', async () => {
      mockPrisma.dashboard.findFirst.mockResolvedValue(mockDashboard);

      const invalidQuery = {
        startDate: '2024-01-31',
        endDate: '2024-01-01', // End before start
        granularity: 'day'
      };

      await expect(
        dashboardService.getDashboardAnalytics(mockDashboard.id, mockUser.id, invalidQuery)
      ).rejects.toThrow('Invalid date range');
    });
  });
});