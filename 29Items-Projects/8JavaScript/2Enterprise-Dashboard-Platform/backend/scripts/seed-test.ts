import { PrismaClient } from '@prisma/client';
import bcrypt from 'bcrypt';

const prisma = new PrismaClient();

async function seedTestData() {
  console.log('🌱 Seeding test data...');

  try {
    // Clear existing test data
    await prisma.widget.deleteMany();
    await prisma.dashboard.deleteMany();
    await prisma.user.deleteMany();

    // Create test users
    const hashedPassword = await bcrypt.hash('password123', 10);

    const adminUser = await prisma.user.create({
      data: {
        email: 'admin@test.com',
        password: hashedPassword,
        name: 'Test Admin',
        role: 'ADMIN',
      },
    });

    const regularUser = await prisma.user.create({
      data: {
        email: 'user@test.com',
        password: hashedPassword,
        name: 'Test User',
        role: 'USER',
      },
    });

    // Create E2E test user
    const e2eUser = await prisma.user.create({
      data: {
        email: 'e2e@example.com',
        password: await bcrypt.hash('e2epassword123', 10),
        name: 'E2E Test User',
        role: 'USER',
      },
    });

    // Create test dashboards
    const adminDashboard = await prisma.dashboard.create({
      data: {
        name: 'Admin Dashboard',
        description: 'Administrative overview dashboard',
        layout: [
          { i: 'widget-1', x: 0, y: 0, w: 6, h: 4 },
          { i: 'widget-2', x: 6, y: 0, w: 6, h: 4 },
          { i: 'widget-3', x: 0, y: 4, w: 12, h: 6 },
        ],
        settings: {
          theme: 'light',
          autoRefresh: true,
          refreshInterval: 30000,
        },
        userId: adminUser.id,
      },
    });

    const userDashboard = await prisma.dashboard.create({
      data: {
        name: 'User Dashboard',
        description: 'Personal dashboard',
        layout: [
          { i: 'widget-4', x: 0, y: 0, w: 8, h: 6 },
          { i: 'widget-5', x: 8, y: 0, w: 4, h: 6 },
        ],
        settings: {
          theme: 'dark',
          autoRefresh: false,
          refreshInterval: 60000,
        },
        userId: regularUser.id,
      },
    });

    const e2eDashboard = await prisma.dashboard.create({
      data: {
        name: 'E2E Test Dashboard',
        description: 'Dashboard for E2E testing',
        layout: [
          { i: 'e2e-widget-1', x: 0, y: 0, w: 6, h: 4 },
          { i: 'e2e-widget-2', x: 6, y: 0, w: 6, h: 4 },
        ],
        settings: {
          theme: 'light',
          autoRefresh: true,
          refreshInterval: 30000,
        },
        userId: e2eUser.id,
      },
    });

    // Create test widgets
    const widgets = [
      {
        id: 'widget-1',
        type: 'CHART' as const,
        title: 'Performance Metrics',
        config: {
          chartType: 'line',
          dataSource: 'metrics',
          metrics: ['cpu', 'memory', 'disk'],
          timeRange: '24h',
        },
        position: { x: 0, y: 0, w: 6, h: 4 },
        dashboardId: adminDashboard.id,
      },
      {
        id: 'widget-2',
        type: 'KPI' as const,
        title: 'Key Metrics',
        config: {
          metrics: [
            { name: 'Active Users', value: 1234, change: 12.5 },
            { name: 'Revenue', value: '$45,678', change: -2.3 },
            { name: 'Conversion Rate', value: '3.4%', change: 0.8 },
          ],
        },
        position: { x: 6, y: 0, w: 6, h: 4 },
        dashboardId: adminDashboard.id,
      },
      {
        id: 'widget-3',
        type: 'TABLE' as const,
        title: 'Recent Activities',
        config: {
          dataSource: 'activities',
          columns: ['timestamp', 'user', 'action', 'status'],
          pageSize: 10,
        },
        position: { x: 0, y: 4, w: 12, h: 6 },
        dashboardId: adminDashboard.id,
      },
      {
        id: 'widget-4',
        type: 'CHART' as const,
        title: 'User Analytics',
        config: {
          chartType: 'bar',
          dataSource: 'analytics',
          metrics: ['pageViews', 'sessions', 'bounceRate'],
          timeRange: '7d',
        },
        position: { x: 0, y: 0, w: 8, h: 6 },
        dashboardId: userDashboard.id,
      },
      {
        id: 'widget-5',
        type: 'KPI' as const,
        title: 'Personal Stats',
        config: {
          metrics: [
            { name: 'Tasks Completed', value: 23, change: 15.2 },
            { name: 'Projects', value: 5, change: 0 },
            { name: 'Team Score', value: 8.7, change: 1.2 },
          ],
        },
        position: { x: 8, y: 0, w: 4, h: 6 },
        dashboardId: userDashboard.id,
      },
      {
        id: 'e2e-widget-1',
        type: 'CHART' as const,
        title: 'E2E Test Chart',
        config: {
          chartType: 'line',
          dataSource: 'test-metrics',
          metrics: ['testMetric1', 'testMetric2'],
          timeRange: '1h',
        },
        position: { x: 0, y: 0, w: 6, h: 4 },
        dashboardId: e2eDashboard.id,
      },
      {
        id: 'e2e-widget-2',
        type: 'KPI' as const,
        title: 'E2E Test KPIs',
        config: {
          metrics: [
            { name: 'Test Metric', value: 100, change: 0 },
            { name: 'Success Rate', value: '98%', change: 2 },
          ],
        },
        position: { x: 6, y: 0, w: 6, h: 4 },
        dashboardId: e2eDashboard.id,
      },
    ];

    for (const widget of widgets) {
      await prisma.widget.create({
        data: widget,
      });
    }

    console.log('✅ Test data seeding completed successfully!');
    console.log(`Created ${widgets.length} widgets across 3 dashboards for 3 users`);

  } catch (error) {
    console.error('❌ Error seeding test data:', error);
    throw error;
  } finally {
    await prisma.$disconnect();
  }
}

// Run the seed function
seedTestData();