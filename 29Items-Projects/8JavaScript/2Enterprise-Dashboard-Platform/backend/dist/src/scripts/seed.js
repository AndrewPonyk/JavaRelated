import { PrismaClient } from '@prisma/client';
import bcrypt from 'bcryptjs';
import { logger } from '@/utils/logger.js';
const prisma = new PrismaClient();
async function main() {
    try {
        logger.info('Starting database seeding...');
        const adminPassword = await bcrypt.hash('Admin123!', 12);
        const adminUser = await prisma.user.upsert({
            where: { email: 'admin@dashboard.com' },
            update: {},
            create: {
                email: 'admin@dashboard.com',
                password: adminPassword,
                firstName: 'Admin',
                lastName: 'User',
                role: 'SUPER_ADMIN',
                isActive: true,
                profile: {
                    create: {
                        theme: 'dark',
                        timezone: 'UTC',
                        language: 'en',
                        notifications: JSON.stringify({
                            email: true,
                            push: true,
                            dashboard: true,
                            security: true,
                            updates: true,
                        }),
                        bio: 'System Administrator',
                    },
                },
            },
            include: {
                profile: true,
            },
        });
        logger.info('Admin user created/updated', { userId: adminUser.id });
        const demoPassword = await bcrypt.hash('Demo123!', 12);
        const demoUser = await prisma.user.upsert({
            where: { email: 'demo@dashboard.com' },
            update: {},
            create: {
                email: 'demo@dashboard.com',
                password: demoPassword,
                firstName: 'Demo',
                lastName: 'User',
                role: 'USER',
                isActive: true,
                profile: {
                    create: {
                        theme: 'light',
                        timezone: 'America/New_York',
                        language: 'en',
                        notifications: JSON.stringify({
                            email: true,
                            push: false,
                            dashboard: true,
                            security: true,
                            updates: false,
                        }),
                        bio: 'Demo user for testing the dashboard platform',
                    },
                },
            },
            include: {
                profile: true,
            },
        });
        logger.info('Demo user created/updated', { userId: demoUser.id });
        const salesDashboard = await prisma.dashboard.upsert({
            where: { id: 'sample-sales-dashboard' },
            update: {},
            create: {
                id: 'sample-sales-dashboard',
                title: 'Sales Analytics Dashboard',
                description: 'Comprehensive sales performance and analytics dashboard',
                slug: 'sales-analytics-dashboard',
                isPublic: true,
                isTemplate: false,
                userId: demoUser.id,
                layout: JSON.stringify([
                    { id: 'revenue-chart', x: 0, y: 0, width: 6, height: 4 },
                    { id: 'sales-funnel', x: 6, y: 0, width: 6, height: 4 },
                    { id: 'top-products', x: 0, y: 4, width: 4, height: 3 },
                    { id: 'sales-by-region', x: 4, y: 4, width: 4, height: 3 },
                    { id: 'monthly-targets', x: 8, y: 4, width: 4, height: 3 },
                ]),
                settings: JSON.stringify({
                    theme: 'light',
                    refreshInterval: 300,
                    showGrid: true,
                    gridSize: 12,
                    backgroundColor: '#ffffff',
                    compactMode: false,
                    animations: true,
                }),
            },
        });
        logger.info('Sales dashboard created/updated', { dashboardId: salesDashboard.id });
        const marketingDashboard = await prisma.dashboard.upsert({
            where: { id: 'sample-marketing-dashboard' },
            update: {},
            create: {
                id: 'sample-marketing-dashboard',
                title: 'Marketing Performance Dashboard',
                description: 'Track marketing campaigns, conversions, and ROI',
                slug: 'marketing-performance-dashboard',
                isPublic: true,
                isTemplate: true,
                userId: demoUser.id,
                layout: JSON.stringify([
                    { id: 'campaign-performance', x: 0, y: 0, width: 8, height: 4 },
                    { id: 'conversion-rate', x: 8, y: 0, width: 4, height: 4 },
                    { id: 'traffic-sources', x: 0, y: 4, width: 6, height: 4 },
                    { id: 'social-metrics', x: 6, y: 4, width: 6, height: 4 },
                ]),
                settings: JSON.stringify({
                    theme: 'dark',
                    refreshInterval: 600,
                    showGrid: true,
                    gridSize: 12,
                    backgroundColor: '#1a1a1a',
                    compactMode: false,
                    animations: true,
                }),
            },
        });
        logger.info('Marketing dashboard created/updated', { dashboardId: marketingDashboard.id });
        const widgets = [
            {
                id: 'revenue-chart',
                title: 'Monthly Revenue',
                description: 'Revenue trends over the last 12 months',
                type: 'CHART_LINE',
                dashboardId: salesDashboard.id,
                userId: demoUser.id,
                config: JSON.stringify({
                    chartType: 'line',
                    showLegend: true,
                    showGrid: true,
                    showTooltip: true,
                    colorScheme: ['#3B82F6', '#10B981', '#F59E0B'],
                    xAxis: 'month',
                    yAxis: ['revenue', 'profit'],
                    aggregation: 'sum',
                }),
                position: JSON.stringify({ x: 0, y: 0, width: 6, height: 4 }),
                query: 'SELECT month, SUM(revenue) as revenue, SUM(profit) as profit FROM sales GROUP BY month ORDER BY month',
                refreshRate: 300,
            },
            {
                id: 'sales-funnel',
                title: 'Sales Funnel',
                description: 'Customer acquisition and conversion funnel',
                type: 'CHART_BAR',
                dashboardId: salesDashboard.id,
                userId: demoUser.id,
                config: JSON.stringify({
                    chartType: 'bar',
                    showLegend: false,
                    showGrid: false,
                    showTooltip: true,
                    colorScheme: ['#EF4444', '#F97316', '#EAB308', '#22C55E'],
                }),
                position: JSON.stringify({ x: 6, y: 0, width: 6, height: 4 }),
                refreshRate: 600,
            },
            {
                id: 'top-products',
                title: 'Top Products',
                description: 'Best selling products this month',
                type: 'TABLE',
                dashboardId: salesDashboard.id,
                userId: demoUser.id,
                config: JSON.stringify({
                    sortable: true,
                    filterable: false,
                    pagination: false,
                    rowsPerPage: 10,
                }),
                position: JSON.stringify({ x: 0, y: 4, width: 4, height: 3 }),
                refreshRate: 1800,
            },
            {
                id: 'sales-by-region',
                title: 'Sales by Region',
                description: 'Geographic distribution of sales',
                type: 'CHART_PIE',
                dashboardId: salesDashboard.id,
                userId: demoUser.id,
                config: JSON.stringify({
                    chartType: 'pie',
                    showLegend: true,
                    colorScheme: ['#3B82F6', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6'],
                }),
                position: JSON.stringify({ x: 4, y: 4, width: 4, height: 3 }),
                refreshRate: 900,
            },
            {
                id: 'monthly-targets',
                title: 'Monthly Targets',
                description: 'Progress towards monthly sales targets',
                type: 'GAUGE',
                dashboardId: salesDashboard.id,
                userId: demoUser.id,
                config: JSON.stringify({
                    format: 'percentage',
                    showTitle: true,
                    colorScheme: ['#22C55E', '#EAB308', '#EF4444'],
                }),
                position: JSON.stringify({ x: 8, y: 4, width: 4, height: 3 }),
                refreshRate: 300,
            },
        ];
        for (const widgetData of widgets) {
            await prisma.widget.upsert({
                where: { id: widgetData.id },
                update: {},
                create: widgetData,
            });
        }
        logger.info('Sample widgets created/updated', { count: widgets.length });
        const analyticsData = [];
        const now = new Date();
        for (let i = 29; i >= 0; i--) {
            const date = new Date(now);
            date.setDate(date.getDate() - i);
            date.setHours(0, 0, 0, 0);
            analyticsData.push({
                dashboardId: salesDashboard.id,
                date,
                granularity: 'day',
                views: Math.floor(Math.random() * 100) + 20,
                uniqueViews: Math.floor(Math.random() * 80) + 15,
                avgLoadTime: Math.random() * 2 + 0.5,
                bounceRate: Math.random() * 0.3 + 0.1,
            });
        }
        for (const analytics of analyticsData) {
            await prisma.dashboardAnalytics.upsert({
                where: {
                    dashboardId_date_granularity: {
                        dashboardId: analytics.dashboardId,
                        date: analytics.date,
                        granularity: analytics.granularity,
                    },
                },
                update: {},
                create: analytics,
            });
        }
        logger.info('Sample analytics data created/updated', { count: analyticsData.length });
        await prisma.dashboardShare.upsert({
            where: {
                dashboardId_userId: {
                    dashboardId: marketingDashboard.id,
                    userId: adminUser.id,
                },
            },
            update: {},
            create: {
                dashboardId: marketingDashboard.id,
                userId: adminUser.id,
                permission: 'READ',
                sharedBy: demoUser.id,
            },
        });
        logger.info('Sample dashboard share created/updated');
        const activities = [
            {
                action: 'DASHBOARD_CREATED',
                entity: 'dashboard',
                entityId: salesDashboard.id,
                userId: demoUser.id,
                details: JSON.stringify({ title: salesDashboard.title }),
            },
            {
                action: 'DASHBOARD_VIEWED',
                entity: 'dashboard',
                entityId: salesDashboard.id,
                userId: adminUser.id,
                details: JSON.stringify({ title: salesDashboard.title }),
            },
            {
                action: 'WIDGET_CREATED',
                entity: 'widget',
                entityId: 'revenue-chart',
                userId: demoUser.id,
                details: JSON.stringify({ title: 'Monthly Revenue', type: 'CHART_LINE' }),
            },
        ];
        for (const activity of activities) {
            await prisma.activityLog.create({
                data: activity,
            });
        }
        logger.info('Sample activity logs created', { count: activities.length });
        logger.info('Database seeding completed successfully!');
        logger.info('Seed Summary:', {
            users: 2,
            dashboards: 2,
            widgets: widgets.length,
            analyticsRecords: analyticsData.length,
            shares: 1,
            activityLogs: activities.length,
        });
        console.log('\n🎉 Database seeding completed successfully!');
        console.log('\n📋 Created accounts:');
        console.log('   Admin: admin@dashboard.com / Admin123!');
        console.log('   Demo:  demo@dashboard.com / Demo123!');
        console.log('\n📊 Created sample dashboards:');
        console.log('   - Sales Analytics Dashboard (public)');
        console.log('   - Marketing Performance Dashboard (template)');
        console.log(`\n📈 Generated ${analyticsData.length} days of analytics data`);
        console.log(`\n🔧 Created ${widgets.length} sample widgets`);
    }
    catch (error) {
        logger.error('Database seeding failed', { error });
        throw error;
    }
    finally {
        await prisma.$disconnect();
    }
}
main()
    .then(() => {
    process.exit(0);
})
    .catch((error) => {
    console.error('Seeding failed:', error);
    process.exit(1);
});
//# sourceMappingURL=seed.js.map