import React, { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { User, Bell, Palette, Shield, Globe, Key, ChevronRight, Save, Moon, Sun, Monitor, Loader2 } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { useToastHelpers } from '@/components/ui/Toaster';
import { queryKeys } from '@/services/cache/queryClient';
import { settingsApi, UserSettings } from '@/services/api/settingsApi';
import { cn } from '@/utils/cn';

type SettingsSection = 'profile' | 'notifications' | 'appearance' | 'security' | 'language' | 'api';

const settingsSections = [
  { id: 'profile' as const, title: 'Profile', description: 'Manage your personal information', icon: User, color: 'blue' },
  { id: 'notifications' as const, title: 'Notifications', description: 'Configure alerts and updates', icon: Bell, color: 'green' },
  { id: 'appearance' as const, title: 'Appearance', description: 'Customize theme and display', icon: Palette, color: 'purple' },
  { id: 'security' as const, title: 'Security', description: 'Manage passwords and 2FA', icon: Shield, color: 'red' },
  { id: 'language' as const, title: 'Language & Region', description: 'Language, timezone settings', icon: Globe, color: 'orange' },
  { id: 'api' as const, title: 'API Keys', description: 'Manage API keys', icon: Key, color: 'gray' },
];

export const Settings: React.FC = () => {
  const [activeSection, setActiveSection] = useState<SettingsSection>('profile');
  const { success: showSuccess, error: showError } = useToastHelpers();
  const queryClient = useQueryClient();

  // Fetch user settings
  const { data: settingsData, isLoading } = useQuery({
    queryKey: queryKeys.settings.user(),
    queryFn: settingsApi.getUserSettings,
    staleTime: 5 * 60 * 1000,
  });

  // Local state for form values
  const [theme, setTheme] = useState<'light' | 'dark' | 'auto'>('light');
  const [language, setLanguage] = useState('en');
  const [timezone, setTimezone] = useState('UTC');
  const [notifications, setNotifications] = useState({
    email: true,
    push: true,
    dashboard: true,
    security: true,
    updates: false,
  });

  // Initialize form with fetched data
  useEffect(() => {
    if (settingsData?.data) {
      const settings = settingsData.data;
      setTheme(settings.theme || 'light');
      setLanguage(settings.language || 'en');
      setTimezone(settings.timezone || 'UTC');
      if (settings.notifications) {
        setNotifications({
          email: settings.notifications.email ?? true,
          push: settings.notifications.push ?? true,
          dashboard: settings.notifications.dashboard ?? true,
          security: settings.notifications.security ?? true,
          updates: settings.notifications.updates ?? false,
        });
      }
    }
  }, [settingsData]);

  // Update settings mutation
  const updateMutation = useMutation({
    mutationFn: (settings: Partial<UserSettings>) => settingsApi.updateUserSettings(settings),
    onSuccess: () => {
      showSuccess('Settings saved successfully');
      queryClient.invalidateQueries({ queryKey: queryKeys.settings.user() });

      // Apply theme change to document
      if (theme === 'dark') {
        document.documentElement.classList.add('dark');
        localStorage.setItem('theme', 'dark');
      } else if (theme === 'light') {
        document.documentElement.classList.remove('dark');
        localStorage.setItem('theme', 'light');
      } else {
        // System preference
        localStorage.setItem('theme', 'auto');
        const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
        if (prefersDark) {
          document.documentElement.classList.add('dark');
        } else {
          document.documentElement.classList.remove('dark');
        }
      }
    },
    onError: () => {
      showError('Failed to save settings');
    }
  });

  const handleSave = () => {
    updateMutation.mutate({
      theme,
      language,
      timezone,
      notifications,
    });
  };

  const getColorClasses = (color: string) => {
    const colors: Record<string, string> = {
      blue: 'bg-blue-50 text-blue-600',
      green: 'bg-green-50 text-green-600',
      purple: 'bg-purple-50 text-purple-600',
      red: 'bg-red-50 text-red-600',
      orange: 'bg-orange-50 text-orange-600',
      gray: 'bg-gray-100 text-gray-600',
    };
    return colors[color] || 'bg-gray-100 text-gray-600';
  };

  const renderContent = () => {
    if (isLoading) {
      return (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="h-8 w-8 animate-spin text-blue-500" />
        </div>
      );
    }

    switch (activeSection) {
      case 'profile':
        return (
          <div className="space-y-6">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <Label>First Name</Label>
                <Input defaultValue="John" className="mt-1" />
              </div>
              <div>
                <Label>Last Name</Label>
                <Input defaultValue="Doe" className="mt-1" />
              </div>
            </div>
            <div>
              <Label>Email</Label>
              <Input type="email" defaultValue="john@example.com" className="mt-1" />
            </div>
            <div>
              <Label>Bio</Label>
              <textarea
                className="w-full mt-1 rounded-md border border-gray-300 px-3 py-2 text-sm"
                rows={3}
                defaultValue="Dashboard enthusiast and data analyst."
              />
            </div>
          </div>
        );

      case 'notifications':
        return (
          <div className="space-y-4">
            {Object.entries({
              email: 'Email Notifications',
              push: 'Push Notifications',
              dashboard: 'Dashboard Updates',
              security: 'Security Alerts',
              updates: 'Product Updates',
            }).map(([key, label]) => (
              <div key={key} className="flex items-center justify-between py-3 border-b">
                <div>
                  <p className="font-medium text-gray-900">{label}</p>
                  <p className="text-sm text-gray-500">Receive notifications for {label.toLowerCase()}</p>
                </div>
                <button
                  onClick={() => setNotifications(prev => ({ ...prev, [key]: !prev[key as keyof typeof prev] }))}
                  className={cn(
                    'relative w-11 h-6 rounded-full transition-colors',
                    notifications[key as keyof typeof notifications] ? 'bg-blue-600' : 'bg-gray-200'
                  )}
                >
                  <span
                    className={cn(
                      'absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full transition-transform',
                      notifications[key as keyof typeof notifications] && 'translate-x-5'
                    )}
                  />
                </button>
              </div>
            ))}
          </div>
        );

      case 'appearance':
        return (
          <div className="space-y-6">
            <div>
              <Label className="mb-3 block">Theme</Label>
              <div className="grid grid-cols-3 gap-3">
                {[
                  { value: 'light', label: 'Light', icon: Sun },
                  { value: 'dark', label: 'Dark', icon: Moon },
                  { value: 'auto', label: 'System', icon: Monitor },
                ].map(({ value, label, icon: Icon }) => (
                  <button
                    key={value}
                    onClick={() => setTheme(value as typeof theme)}
                    className={cn(
                      'p-4 rounded-lg border-2 flex flex-col items-center gap-2 transition-colors',
                      theme === value ? 'border-blue-500 bg-blue-50' : 'border-gray-200 hover:border-gray-300'
                    )}
                  >
                    <Icon className="h-6 w-6" />
                    <span className="text-sm font-medium">{label}</span>
                  </button>
                ))}
              </div>
            </div>
            <div>
              <Label>Accent Color</Label>
              <div className="flex gap-2 mt-2">
                {['blue', 'purple', 'green', 'orange', 'pink'].map(color => (
                  <button
                    key={color}
                    className={cn(
                      'w-8 h-8 rounded-full border-2 border-white shadow-md',
                      `bg-${color}-500`
                    )}
                    style={{ backgroundColor: color === 'blue' ? '#3b82f6' : color === 'purple' ? '#8b5cf6' : color === 'green' ? '#22c55e' : color === 'orange' ? '#f97316' : '#ec4899' }}
                  />
                ))}
              </div>
            </div>
            <div>
              <Label>Font Size</Label>
              <select className="w-full mt-1 rounded-md border border-gray-300 px-3 py-2">
                <option>Small</option>
                <option>Medium (Default)</option>
                <option>Large</option>
              </select>
            </div>
          </div>
        );

      case 'security':
        return (
          <div className="space-y-6">
            <div>
              <Label>Current Password</Label>
              <Input type="password" className="mt-1" placeholder="Enter current password" />
            </div>
            <div>
              <Label>New Password</Label>
              <Input type="password" className="mt-1" placeholder="Enter new password" />
            </div>
            <div>
              <Label>Confirm New Password</Label>
              <Input type="password" className="mt-1" placeholder="Confirm new password" />
            </div>
            <hr className="my-6" />
            <div className="flex items-center justify-between py-3">
              <div>
                <p className="font-medium text-gray-900">Two-Factor Authentication</p>
                <p className="text-sm text-gray-500">Add an extra layer of security to your account</p>
              </div>
              <Button variant="outline">Enable 2FA</Button>
            </div>
            <div className="flex items-center justify-between py-3">
              <div>
                <p className="font-medium text-gray-900">Active Sessions</p>
                <p className="text-sm text-gray-500">Manage devices where you're logged in</p>
              </div>
              <Button variant="outline">View Sessions</Button>
            </div>
          </div>
        );

      case 'language':
        return (
          <div className="space-y-6">
            <div>
              <Label>Language</Label>
              <select
                value={language}
                onChange={(e) => setLanguage(e.target.value)}
                className="w-full mt-1 rounded-md border border-gray-300 px-3 py-2"
              >
                <option value="en">English</option>
                <option value="es">Spanish</option>
                <option value="fr">French</option>
                <option value="de">German</option>
                <option value="zh">Chinese</option>
                <option value="ja">Japanese</option>
              </select>
            </div>
            <div>
              <Label>Timezone</Label>
              <select
                value={timezone}
                onChange={(e) => setTimezone(e.target.value)}
                className="w-full mt-1 rounded-md border border-gray-300 px-3 py-2"
              >
                <option value="UTC">UTC</option>
                <option value="America/New_York">Eastern Time (ET)</option>
                <option value="America/Chicago">Central Time (CT)</option>
                <option value="America/Denver">Mountain Time (MT)</option>
                <option value="America/Los_Angeles">Pacific Time (PT)</option>
                <option value="Europe/London">London (GMT)</option>
                <option value="Europe/Paris">Paris (CET)</option>
                <option value="Asia/Tokyo">Tokyo (JST)</option>
              </select>
            </div>
            <div>
              <Label>Date Format</Label>
              <select className="w-full mt-1 rounded-md border border-gray-300 px-3 py-2">
                <option>MM/DD/YYYY</option>
                <option>DD/MM/YYYY</option>
                <option>YYYY-MM-DD</option>
              </select>
            </div>
            <div>
              <Label>Time Format</Label>
              <select className="w-full mt-1 rounded-md border border-gray-300 px-3 py-2">
                <option>12-hour (AM/PM)</option>
                <option>24-hour</option>
              </select>
            </div>
          </div>
        );

      case 'api':
        return (
          <div className="space-y-6">
            <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4">
              <p className="text-sm text-yellow-800">
                API keys provide full access to your account. Keep them secure and never share them publicly.
              </p>
            </div>
            <div className="space-y-4">
              <div className="flex items-center justify-between p-4 bg-gray-50 rounded-lg">
                <div>
                  <p className="font-medium text-gray-900">Production Key</p>
                  <p className="text-sm text-gray-500 font-mono">sk-****************************abcd</p>
                  <p className="text-xs text-gray-400 mt-1">Created: Jan 15, 2024</p>
                </div>
                <div className="flex gap-2">
                  <Button variant="outline" size="sm">Copy</Button>
                  <Button variant="outline" size="sm" className="text-red-600">Revoke</Button>
                </div>
              </div>
            </div>
            <Button>
              <Key className="mr-2 h-4 w-4" />
              Generate New API Key
            </Button>
          </div>
        );

      default:
        return null;
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Settings</h1>
        <p className="text-gray-600">Manage your account and application preferences</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        {/* Sidebar */}
        <Card className="p-2 lg:col-span-1 h-fit">
          <nav className="space-y-1">
            {settingsSections.map((section) => {
              const Icon = section.icon;
              return (
                <button
                  key={section.id}
                  onClick={() => setActiveSection(section.id)}
                  className={cn(
                    'w-full flex items-center gap-3 px-3 py-2 rounded-lg text-left transition-colors',
                    activeSection === section.id
                      ? 'bg-blue-50 text-blue-700'
                      : 'text-gray-600 hover:bg-gray-50'
                  )}
                >
                  <div className={cn('p-2 rounded-lg', getColorClasses(section.color))}>
                    <Icon className="h-4 w-4" />
                  </div>
                  <div className="flex-1">
                    <p className="font-medium text-sm">{section.title}</p>
                  </div>
                  <ChevronRight className="h-4 w-4 text-gray-400" />
                </button>
              );
            })}
          </nav>
        </Card>

        {/* Content */}
        <Card className="p-6 lg:col-span-3">
          <div className="mb-6">
            <h2 className="text-lg font-semibold text-gray-900">
              {settingsSections.find(s => s.id === activeSection)?.title}
            </h2>
            <p className="text-sm text-gray-500">
              {settingsSections.find(s => s.id === activeSection)?.description}
            </p>
          </div>

          {renderContent()}

          <div className="mt-8 pt-6 border-t flex justify-end">
            <Button onClick={handleSave} disabled={updateMutation.isPending}>
              {updateMutation.isPending ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <Save className="mr-2 h-4 w-4" />
              )}
              Save Changes
            </Button>
          </div>
        </Card>
      </div>
    </div>
  );
};

export default Settings;
