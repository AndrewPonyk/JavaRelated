import React, { useState, useEffect } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Header } from './Header';
import { Sidebar } from './Sidebar';
import { cn } from '@/utils/cn';
import { useLocalStorage } from '@/hooks/ui/useLocalStorage';

interface LayoutProps {
  children?: React.ReactNode;
}

export const Layout: React.FC<LayoutProps> = ({ children }) => {
  const [sidebarOpen, setSidebarOpen] = useLocalStorage('sidebar-open', true);
  const [sidebarCollapsed, setSidebarCollapsed] = useLocalStorage('sidebar-collapsed', false);
  const location = useLocation();

  // Auto-collapse sidebar on mobile
  const [isMobile, setIsMobile] = useState(false);

  useEffect(() => {
    const checkMobile = () => {
      setIsMobile(window.innerWidth < 768);
    };

    checkMobile();
    window.addEventListener('resize', checkMobile);
    return () => window.removeEventListener('resize', checkMobile);
  }, []);

  // Close sidebar on mobile when route changes
  useEffect(() => {
    if (isMobile) {
      setSidebarOpen(false);
    }
  }, [location.pathname, isMobile, setSidebarOpen]);

  // Handle keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Toggle sidebar with Cmd/Ctrl + B
      if ((e.metaKey || e.ctrlKey) && e.key === 'b') {
        e.preventDefault();
        if (isMobile) {
          setSidebarOpen(!sidebarOpen);
        } else {
          setSidebarCollapsed(!sidebarCollapsed);
        }
      }

      // Close sidebar with Escape on mobile
      if (e.key === 'Escape' && isMobile && sidebarOpen) {
        setSidebarOpen(false);
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [isMobile, sidebarOpen, sidebarCollapsed, setSidebarOpen, setSidebarCollapsed]);

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Sidebar */}
      <Sidebar
        open={sidebarOpen}
        collapsed={sidebarCollapsed && !isMobile}
        onOpenChange={setSidebarOpen}
        onCollapsedChange={setSidebarCollapsed}
        isMobile={isMobile}
      />

      {/* Mobile overlay */}
      {isMobile && sidebarOpen && (
        <div
          className="fixed inset-0 z-40 bg-black bg-opacity-50 transition-opacity"
          onClick={() => setSidebarOpen(false)}
          aria-hidden="true"
        />
      )}

      {/* Main content */}
      <div
        className={cn(
          'transition-all duration-300 ease-in-out',
          isMobile
            ? 'ml-0'
            : sidebarCollapsed
            ? 'ml-16'
            : 'ml-64'
        )}
      >
        {/* Header */}
        <Header
          onMenuClick={() => {
            if (isMobile) {
              setSidebarOpen(!sidebarOpen);
            } else {
              setSidebarCollapsed(!sidebarCollapsed);
            }
          }}
          sidebarCollapsed={sidebarCollapsed && !isMobile}
          isMobile={isMobile}
        />

        {/* Page content */}
        <main className="min-h-[calc(100vh-4rem)] p-4 sm:p-6 lg:p-8">
          <div className="mx-auto max-w-screen-2xl">
            {children || <Outlet />}
          </div>
        </main>
      </div>

      {/* Loading state overlay (for future use) */}
      {/* {isLoading && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-white bg-opacity-80">
          <div className="flex items-center space-x-2">
            <div className="h-4 w-4 animate-spin rounded-full border-2 border-blue-600 border-t-transparent"></div>
            <span className="text-sm text-gray-600">Loading...</span>
          </div>
        </div>
      )} */}
    </div>
  );
};

export default Layout;