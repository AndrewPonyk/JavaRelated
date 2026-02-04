import React, { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard,
  BarChart3,
  Settings,
  Users,
  FileText,
  TrendingUp,
  Database,
  Shield,
  Bell,
  HelpCircle,
  ChevronLeft,
  ChevronRight,
  Plus,
  Activity,
  PieChart,
  Calendar,
  Folder,
  Tag,
  Brain,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { useAuth } from '@/components/auth/AuthProvider';
import { cn } from '@/utils/cn';

interface SidebarProps {
  open: boolean;
  collapsed: boolean;
  onOpenChange: (open: boolean) => void;
  onCollapsedChange: (collapsed: boolean) => void;
  isMobile: boolean;
}

interface NavigationItem {
  name: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
  current?: boolean;
  badge?: string | number;
  children?: NavigationItem[];
  requiredRoles?: string[];
}

const navigationItems: NavigationItem[] = [
  {
    name: 'Dashboard',
    href: '/dashboard',
    icon: LayoutDashboard,
  },
  {
    name: 'Analytics',
    href: '/analytics',
    icon: BarChart3,
    children: [
      {
        name: 'ML & AI Analytics',
        href: '/analytics/ml',
        icon: Brain,
      },
      {
        name: 'Reports',
        href: '/analytics/reports',
        icon: FileText,
      },
      {
        name: 'Insights',
        href: '/analytics/insights',
        icon: TrendingUp,
      },
      {
        name: 'Financial',
        href: '/analytics/financial',
        icon: Activity,
      },
    ],
  },
  {
    name: 'Reports',
    href: '/reports',
    icon: FileText,
    badge: '3',
  },
  {
    name: 'Data Sources',
    href: '/data-sources',
    icon: Database,
  },
  {
    name: 'Widgets',
    href: '/widgets',
    icon: PieChart,
    children: [
      {
        name: 'My Widgets',
        href: '/widgets/my',
        icon: Folder,
      },
      {
        name: 'Shared',
        href: '/widgets/shared',
        icon: Users,
      },
      {
        name: 'Templates',
        href: '/widgets/templates',
        icon: Tag,
      },
    ],
  },
  {
    name: 'Calendar',
    href: '/calendar',
    icon: Calendar,
  },
];

const adminNavigationItems: NavigationItem[] = [
  {
    name: 'User Management',
    href: '/admin/users',
    icon: Users,
    requiredRoles: ['ADMIN', 'SUPER_ADMIN'],
  },
  {
    name: 'System Settings',
    href: '/admin/settings',
    icon: Settings,
    requiredRoles: ['ADMIN', 'SUPER_ADMIN'],
  },
  {
    name: 'Security',
    href: '/admin/security',
    icon: Shield,
    requiredRoles: ['SUPER_ADMIN'],
  },
  {
    name: 'Audit Logs',
    href: '/admin/audit',
    icon: Activity,
    requiredRoles: ['ADMIN', 'SUPER_ADMIN'],
  },
];

const bottomNavigationItems: NavigationItem[] = [
  {
    name: 'Notifications',
    href: '/notifications',
    icon: Bell,
    badge: 5,
  },
  {
    name: 'Help & Support',
    href: '/support',
    icon: HelpCircle,
  },
  {
    name: 'Settings',
    href: '/settings',
    icon: Settings,
  },
];

export const Sidebar: React.FC<SidebarProps> = ({
  open,
  collapsed,
  onOpenChange,
  onCollapsedChange,
  isMobile,
}) => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [expandedItems, setExpandedItems] = useState<string[]>([]);

  const toggleExpanded = (itemName: string) => {
    setExpandedItems(prev =>
      prev.includes(itemName)
        ? prev.filter(name => name !== itemName)
        : [...prev, itemName]
    );
  };

  const hasRequiredRole = (requiredRoles?: string[]) => {
    if (!requiredRoles || requiredRoles.length === 0) return true;
    return requiredRoles.includes(user?.role || '');
  };

  const isActive = (href: string) => {
    return location.pathname === href || location.pathname.startsWith(href + '/');
  };

  const renderNavigationItem = (item: NavigationItem, level = 0) => {
    if (!hasRequiredRole(item.requiredRoles)) return null;

    const active = isActive(item.href);
    const hasChildren = item.children && item.children.length > 0;
    const isExpanded = expandedItems.includes(item.name);

    const ItemIcon = item.icon;
    const content = (
      <>
        <div className="flex items-center min-w-0 flex-1">
          <ItemIcon
            className={cn(
              'flex-shrink-0 h-5 w-5',
              active
                ? 'text-blue-600'
                : 'text-gray-400 group-hover:text-gray-500',
              collapsed && !isMobile && 'mx-auto'
            )}
          />
          {(!collapsed || isMobile) && (
            <>
              <span
                className={cn(
                  'ml-3 text-sm font-medium truncate',
                  level > 0 && 'ml-8',
                  active ? 'text-gray-900' : 'text-gray-700 group-hover:text-gray-900'
                )}
              >
                {item.name}
              </span>
              {item.badge && (
                <Badge
                  variant={typeof item.badge === 'number' && item.badge > 0 ? 'destructive' : 'secondary'}
                  className="ml-auto"
                >
                  {item.badge}
                </Badge>
              )}
            </>
          )}
        </div>
        {hasChildren && (!collapsed || isMobile) && (
          <ChevronRight
            className={cn(
              'ml-auto h-4 w-4 transition-transform text-gray-400',
              isExpanded && 'transform rotate-90'
            )}
          />
        )}
      </>
    );

    return (
      <div key={item.name}>
        {hasChildren ? (
          <button
            onClick={() => toggleExpanded(item.name)}
            className={cn(
              'group flex items-center w-full px-3 py-2 text-left text-sm font-medium rounded-md transition-colors',
              active
                ? 'bg-blue-50 text-blue-700 border-r-2 border-blue-600'
                : 'text-gray-700 hover:bg-gray-50 hover:text-gray-900',
              collapsed && !isMobile && 'justify-center px-2'
            )}
          >
            {content}
          </button>
        ) : (
          <Link
            to={item.href}
            className={cn(
              'group flex items-center px-3 py-2 text-sm font-medium rounded-md transition-colors',
              active
                ? 'bg-blue-50 text-blue-700 border-r-2 border-blue-600'
                : 'text-gray-700 hover:bg-gray-50 hover:text-gray-900',
              collapsed && !isMobile && 'justify-center px-2'
            )}
          >
            {content}
          </Link>
        )}

        {hasChildren && isExpanded && (!collapsed || isMobile) && (
          <div className="ml-4 mt-1 space-y-1">
            {item.children?.map(child => renderNavigationItem(child, level + 1))}
          </div>
        )}
      </div>
    );
  };

  const sidebarContent = (
    <div className="flex flex-col h-full">
      {/* Logo */}
      <div className="flex items-center flex-shrink-0 px-4 py-4 border-b border-gray-200">
        {(!collapsed || isMobile) ? (
          <Link to="/dashboard" className="flex items-center">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-600 text-white">
              <span className="text-sm font-bold">ED</span>
            </div>
            <span className="ml-2 text-lg font-semibold text-gray-900">
              Enterprise Dashboard
            </span>
          </Link>
        ) : (
          <Link
            to="/dashboard"
            className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-600 text-white mx-auto"
          >
            <span className="text-sm font-bold">ED</span>
          </Link>
        )}
      </div>

      {/* Navigation */}
      <div className="flex-1 flex flex-col overflow-y-auto">
        {/* Quick actions */}
        {(!collapsed || isMobile) && (
          <div className="p-4 border-b border-gray-200">
            <Button size="sm" className="w-full justify-start" onClick={() => navigate('/dashboard/new')}>
              <Plus className="mr-2 h-4 w-4" />
              New Dashboard
            </Button>
          </div>
        )}

        {/* Main navigation */}
        <nav className="flex-1 px-2 py-4 space-y-1">
          {navigationItems.map(item => renderNavigationItem(item))}
        </nav>

        {/* Admin section */}
        {adminNavigationItems.some(item => hasRequiredRole(item.requiredRoles)) && (
          <div className="border-t border-gray-200">
            {(!collapsed || isMobile) && (
              <div className="px-3 py-2 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                Administration
              </div>
            )}
            <nav className="px-2 py-2 space-y-1">
              {adminNavigationItems
                .filter(item => hasRequiredRole(item.requiredRoles))
                .map(item => renderNavigationItem(item))}
            </nav>
          </div>
        )}

        {/* Bottom navigation */}
        <div className="border-t border-gray-200">
          <nav className="px-2 py-4 space-y-1">
            {bottomNavigationItems.map(item => renderNavigationItem(item))}
          </nav>
        </div>
      </div>

      {/* Collapse button (desktop only) */}
      {!isMobile && (
        <div className="flex-shrink-0 border-t border-gray-200 p-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => onCollapsedChange(!collapsed)}
            className={cn(
              'w-full justify-start',
              collapsed && 'justify-center px-2'
            )}
          >
            {collapsed ? (
              <ChevronRight className="h-4 w-4" />
            ) : (
              <>
                <ChevronLeft className="mr-2 h-4 w-4" />
                Collapse
              </>
            )}
          </Button>
        </div>
      )}
    </div>
  );

  if (isMobile) {
    // Mobile sidebar (overlay)
    return (
      <div
        className={cn(
          'fixed inset-y-0 left-0 z-50 w-64 bg-white shadow-lg transform transition-transform duration-300 ease-in-out',
          open ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        {sidebarContent}
      </div>
    );
  }

  // Desktop sidebar
  return (
    <div
      className={cn(
        'fixed inset-y-0 left-0 z-30 bg-white border-r border-gray-200 transition-all duration-300 ease-in-out',
        collapsed ? 'w-16' : 'w-64'
      )}
    >
      {sidebarContent}
    </div>
  );
};

export default Sidebar;