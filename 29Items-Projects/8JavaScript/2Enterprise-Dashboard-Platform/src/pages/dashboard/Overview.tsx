import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  BarChart3,
  Users,
  TrendingUp,
  DollarSign,
  Plus,
  ArrowUpRight,
  Activity,
  Calendar,
  Clock,
  MoreVertical,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/DropdownMenu';
import { useAuth } from '@/components/auth/AuthProvider';

// Mock data for the dashboard
const statsCards = [
  {
    title: 'Total Revenue',
    value: '$2,451,352',
    change: '+12.5%',
    changeType: 'positive' as const,
    icon: DollarSign,
    description: 'vs last month',
  },
  {
    title: 'Active Users',
    value: '14,532',
    change: '+8.2%',
    changeType: 'positive' as const,
    icon: Users,
    description: 'vs last month',
  },
  {
    title: 'Conversion Rate',
    value: '3.24%',
    change: '-0.4%',
    changeType: 'negative' as const,
    icon: TrendingUp,
    description: 'vs last month',
  },
  {
    title: 'Active Sessions',
    value: '1,234',
    change: '+5.1%',
    changeType: 'positive' as const,
    icon: Activity,
    description: 'right now',
  },
];

const recentDashboards = [
  {
    id: 'sales-performance',
    name: 'Sales Performance',
    description: 'Track sales metrics and KPIs',
    lastAccessed: '2 hours ago',
    status: 'active',
    widgets: 12,
    thumbnail: '📊',
    createdBy: 'John Doe',
    isShared: false,
  },
  {
    id: 'marketing-analytics',
    name: 'Marketing Analytics',
    description: 'Campaign performance and ROI',
    lastAccessed: '1 day ago',
    status: 'active',
    widgets: 8,
    thumbnail: '📈',
    createdBy: 'Jane Smith',
    isShared: true,
  },
  {
    id: 'financial-overview',
    name: 'Financial Overview',
    description: 'Revenue, expenses, and profit analysis',
    lastAccessed: '3 days ago',
    status: 'active',
    widgets: 15,
    thumbnail: '💰',
    createdBy: 'Current User',
    isShared: false,
  },
  {
    id: 'user-engagement',
    name: 'User Engagement',
    description: 'User behavior and engagement metrics',
    lastAccessed: '1 week ago',
    status: 'inactive',
    widgets: 6,
    thumbnail: '👥',
    createdBy: 'Bob Johnson',
    isShared: true,
  },
];

const quickActions = [
  {
    title: 'Create Dashboard',
    description: 'Start with a blank canvas',
    icon: Plus,
    href: '/dashboard/new',
    color: 'bg-blue-500',
  },
  {
    title: 'Browse Templates',
    description: 'Use pre-built dashboard templates',
    icon: BarChart3,
    href: '/dashboard/templates',
    color: 'bg-green-500',
  },
  {
    title: 'Import Dashboard',
    description: 'Import from file or URL',
    icon: Activity,
    href: '/dashboard/import',
    color: 'bg-purple-500',
  },
  {
    title: 'View Reports',
    description: 'Access generated reports',
    icon: Calendar,
    href: '/reports',
    color: 'bg-orange-500',
  },
];

export const DashboardOverview: React.FC = () => {
  const { user } = useAuth();
  const navigate = useNavigate();

  const getGreeting = () => {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 18) return 'Good afternoon';
    return 'Good evening';
  };

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">
            {getGreeting()}, {user?.firstName || 'User'}!
          </h1>
          <p className="text-gray-600">
            Here's what's happening with your data today.
          </p>
        </div>
        <div className="flex items-center space-x-3">
          <Button variant="outline" size="sm">
            <Clock className="mr-2 h-4 w-4" />
            Last updated: {new Date().toLocaleTimeString()}
          </Button>
          <Button onClick={() => navigate('/dashboard/new')}>
            <Plus className="mr-2 h-4 w-4" />
            New Dashboard
          </Button>
        </div>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {statsCards.map((stat, index) => {
          const Icon = stat.icon;
          return (
            <Card key={index} className="p-6">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-gray-600">{stat.title}</p>
                  <p className="text-2xl font-bold text-gray-900">{stat.value}</p>
                </div>
                <div className="p-3 bg-gray-100 rounded-full">
                  <Icon className="h-6 w-6 text-gray-600" />
                </div>
              </div>
              <div className="mt-4 flex items-center justify-between">
                <div className="flex items-center space-x-2">
                  <span
                    className={`inline-flex items-center text-sm font-medium ${
                      stat.changeType === 'positive'
                        ? 'text-green-600'
                        : 'text-red-600'
                    }`}
                  >
                    <ArrowUpRight
                      className={`mr-1 h-4 w-4 ${
                        stat.changeType === 'negative' ? 'rotate-90' : ''
                      }`}
                    />
                    {stat.change}
                  </span>
                  <span className="text-sm text-gray-500">{stat.description}</span>
                </div>
              </div>
            </Card>
          );
        })}
      </div>

      {/* Quick Actions */}
      <Card className="p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Quick Actions</h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {quickActions.map((action, index) => {
            const Icon = action.icon;
            return (
              <Link
                key={index}
                to={action.href}
                className="flex items-center p-4 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
              >
                <div className={`p-2 rounded-lg ${action.color} text-white mr-4`}>
                  <Icon className="h-5 w-5" />
                </div>
                <div>
                  <p className="font-medium text-gray-900">{action.title}</p>
                  <p className="text-sm text-gray-600">{action.description}</p>
                </div>
              </Link>
            );
          })}
        </div>
      </Card>

      {/* Recent Dashboards */}
      <Card className="p-6">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-lg font-semibold text-gray-900">Recent Dashboards</h2>
          <Link
            to="/dashboards"
            className="text-sm text-blue-600 hover:text-blue-700 font-medium"
          >
            View all
          </Link>
        </div>
        <div className="space-y-4">
          {recentDashboards.map((dashboard) => (
            <div
              key={dashboard.id}
              className="group flex items-center justify-between p-4 border border-gray-200 rounded-lg hover:bg-gray-50 hover:border-gray-300 transition-all cursor-pointer"
              onClick={() => window.location.href = `/dashboard/${dashboard.id}`}
            >
              <div className="flex items-center space-x-4">
                <div className="p-3 bg-blue-100 rounded-lg text-2xl">
                  {dashboard.thumbnail}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center space-x-2 mb-1">
                    <h3 className="font-medium text-gray-900 truncate group-hover:text-blue-600 transition-colors">
                      {dashboard.name}
                    </h3>
                    {dashboard.isShared && (
                      <Badge variant="secondary" className="text-xs">
                        Shared
                      </Badge>
                    )}
                  </div>
                  <p className="text-sm text-gray-600 truncate">{dashboard.description}</p>
                  <div className="flex items-center space-x-4 mt-2">
                    <span className="text-xs text-gray-500 flex items-center">
                      <BarChart3 className="h-3 w-3 mr-1" />
                      {dashboard.widgets} widgets
                    </span>
                    <span className="text-xs text-gray-500">
                      by {dashboard.createdBy}
                    </span>
                    <span className="text-xs text-gray-500">
                      {dashboard.lastAccessed}
                    </span>
                  </div>
                </div>
              </div>
              <div className="flex items-center space-x-2">
                <Badge
                  variant={dashboard.status === 'active' ? 'success' : 'secondary'}
                >
                  {dashboard.status}
                </Badge>
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={(e) => e.stopPropagation()}
                    >
                      <MoreVertical className="h-4 w-4" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem
                      onClick={(e) => {
                        e.stopPropagation();
                        window.location.href = `/dashboard/${dashboard.id}`;
                      }}
                    >
                      <BarChart3 className="mr-2 h-4 w-4" />
                      Open Dashboard
                    </DropdownMenuItem>
                    <DropdownMenuItem
                      onClick={(e) => {
                        e.stopPropagation();
                        window.location.href = `/dashboard/${dashboard.id}?edit=true`;
                      }}
                    >
                      <Plus className="mr-2 h-4 w-4" />
                      Edit Dashboard
                    </DropdownMenuItem>
                    <DropdownMenuItem onClick={(e) => e.stopPropagation()}>
                      <ArrowUpRight className="mr-2 h-4 w-4" />
                      Share
                    </DropdownMenuItem>
                    <DropdownMenuItem onClick={(e) => e.stopPropagation()}>
                      <Plus className="mr-2 h-4 w-4" />
                      Duplicate
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem
                      onClick={(e) => e.stopPropagation()}
                      className="text-red-600 focus:text-red-600"
                    >
                      <Plus className="mr-2 h-4 w-4" />
                      Delete
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
};

export default DashboardOverview;