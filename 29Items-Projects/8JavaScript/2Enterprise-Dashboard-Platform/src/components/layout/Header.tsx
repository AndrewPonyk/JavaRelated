import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Menu,
  Search,
  Bell,
  Settings,
  User,
  LogOut,
  ChevronDown,
  Plus,
  HelpCircle,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/Avatar';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/DropdownMenu';
import { Badge } from '@/components/ui/Badge';
import { SearchCommand } from '@/components/ui/SearchCommand';
import { useAuth } from '@/components/auth/AuthProvider';
import { cn } from '@/utils/cn';

interface HeaderProps {
  onMenuClick: () => void;
  sidebarCollapsed: boolean;
  isMobile: boolean;
}

export const Header: React.FC<HeaderProps> = ({
  onMenuClick,
  sidebarCollapsed,
  isMobile,
}) => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [searchOpen, setSearchOpen] = useState(false);

  const handleLogout = () => {
    logout();
  };

  const handleCreateDashboard = () => {
    navigate('/dashboard/new');
  };

  const userInitials = user?.firstName && user?.lastName
    ? `${user.firstName[0]}${user.lastName[0]}`
    : user?.email?.[0]?.toUpperCase() || 'U';

  const userName = user?.firstName && user?.lastName
    ? `${user.firstName} ${user.lastName}`
    : user?.email || 'User';

  return (
    <header className="sticky top-0 z-30 border-b border-gray-200 bg-white shadow-sm">
      <div className="flex h-16 items-center justify-between px-4 sm:px-6 lg:px-8">
        {/* Left side */}
        <div className="flex items-center space-x-4">
          {/* Menu button */}
          <Button
            variant="ghost"
            size="icon"
            onClick={onMenuClick}
            className="shrink-0"
            aria-label={isMobile ? 'Toggle sidebar' : 'Toggle sidebar collapsed'}
          >
            <Menu className="h-5 w-5" />
          </Button>

          {/* Logo and title (hidden when sidebar is expanded on desktop) */}
          {(isMobile || sidebarCollapsed) && (
            <Link
              to="/dashboard"
              className="flex items-center space-x-2 text-xl font-bold text-gray-900"
            >
              <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-600 text-white">
                <span className="text-sm font-bold">ED</span>
              </div>
              <span className="hidden sm:block">Enterprise Dashboard</span>
            </Link>
          )}
        </div>

        {/* Center - Search (hidden on mobile) */}
        <div className="hidden md:flex flex-1 max-w-lg mx-8">
          <Button
            variant="outline"
            onClick={() => setSearchOpen(true)}
            className="w-full justify-start text-gray-500 hover:text-gray-700"
          >
            <Search className="mr-2 h-4 w-4" />
            <span>Search dashboards, widgets...</span>
            <kbd className="ml-auto hidden sm:inline-flex h-5 select-none items-center gap-1 rounded border bg-muted px-1.5 font-mono text-[10px] font-medium text-muted-foreground">
              <span className="text-xs">⌘</span>K
            </kbd>
          </Button>
        </div>

        {/* Right side */}
        <div className="flex items-center space-x-2 sm:space-x-4">
          {/* Mobile search */}
          <Button
            variant="ghost"
            size="icon"
            onClick={() => setSearchOpen(true)}
            className="md:hidden"
            aria-label="Search"
          >
            <Search className="h-5 w-5" />
          </Button>

          {/* Create dashboard button */}
          <Button
            onClick={handleCreateDashboard}
            size="sm"
            className="hidden sm:inline-flex"
          >
            <Plus className="mr-2 h-4 w-4" />
            New Dashboard
          </Button>

          <Button
            onClick={handleCreateDashboard}
            size="icon"
            className="sm:hidden"
            aria-label="Create dashboard"
          >
            <Plus className="h-4 w-4" />
          </Button>

          {/* Notifications */}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon" className="relative">
                <Bell className="h-5 w-5" />
                <Badge
                  variant="destructive"
                  className="absolute -top-1 -right-1 h-5 w-5 p-0 text-xs"
                >
                  3
                </Badge>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-80">
              <DropdownMenuLabel>Notifications</DropdownMenuLabel>
              <DropdownMenuSeparator />
              <div className="space-y-2 p-2">
                <div className="rounded-lg border p-3 text-sm">
                  <div className="font-medium">Dashboard shared with you</div>
                  <div className="text-gray-500">
                    John Doe shared "Sales Analytics" dashboard
                  </div>
                  <div className="mt-1 text-xs text-gray-400">2 hours ago</div>
                </div>
                <div className="rounded-lg border p-3 text-sm">
                  <div className="font-medium">Anomaly detected</div>
                  <div className="text-gray-500">
                    Unusual spike in revenue detected in Q4 dashboard
                  </div>
                  <div className="mt-1 text-xs text-gray-400">4 hours ago</div>
                </div>
                <div className="rounded-lg border p-3 text-sm">
                  <div className="font-medium">Widget updated</div>
                  <div className="text-gray-500">
                    "Monthly Revenue" widget has new data available
                  </div>
                  <div className="mt-1 text-xs text-gray-400">1 day ago</div>
                </div>
              </div>
              <DropdownMenuSeparator />
              <DropdownMenuItem className="justify-center" onClick={() => navigate('/notifications')}>
                View all notifications
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>

          {/* Help */}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon">
                <HelpCircle className="h-5 w-5" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={() => navigate('/support')}>
                <span>Documentation</span>
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => navigate('/support')}>
                <span>Keyboard shortcuts</span>
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => navigate('/support')}>
                <span>Contact support</span>
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={() => navigate('/support')}>
                <span>What's new</span>
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>

          {/* User menu */}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" className="p-0 h-auto">
                <div className="flex items-center space-x-2">
                  <Avatar className="h-8 w-8">
                    <AvatarImage
                      src={user?.profile?.avatar}
                      alt={userName}
                    />
                    <AvatarFallback className="text-sm">
                      {userInitials}
                    </AvatarFallback>
                  </Avatar>
                  <div className="hidden sm:block text-left">
                    <div className="text-sm font-medium">{userName}</div>
                    <div className="text-xs text-gray-500 capitalize">
                      {user?.role?.toLowerCase().replace('_', ' ')}
                    </div>
                  </div>
                  <ChevronDown className="h-4 w-4 text-gray-400" />
                </div>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-56">
              <DropdownMenuLabel>
                <div>
                  <div className="font-medium">{userName}</div>
                  <div className="text-sm text-gray-500">{user?.email}</div>
                </div>
              </DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={() => navigate('/profile')}>
                <User className="mr-2 h-4 w-4" />
                <span>Profile</span>
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => navigate('/profile/settings')}>
                <Settings className="mr-2 h-4 w-4" />
                <span>Settings</span>
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              {(user?.role === 'SUPER_ADMIN' || user?.role === 'ADMIN') && (
                <>
                  <DropdownMenuItem onClick={() => navigate('/admin/users')}>
                    <Settings className="mr-2 h-4 w-4" />
                    <span>Admin Panel</span>
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                </>
              )}
              <DropdownMenuItem onClick={handleLogout}>
                <LogOut className="mr-2 h-4 w-4" />
                <span>Log out</span>
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      {/* Search Command Dialog */}
      <SearchCommand open={searchOpen} onOpenChange={setSearchOpen} />
    </header>
  );
};

export default Header;