import React, { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Settings, Globe, Mail, Database, Bell, Palette, Shield, Save,
  Loader2, CheckCircle, XCircle, RefreshCw, AlertTriangle, Lock,
  Key, Eye, EyeOff, Moon, Sun, Monitor
} from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { useToastHelpers } from '@/components/ui/Toaster';
import { settingsApi } from '@/services/api/settingsApi';
import { queryKeys } from '@/services/cache/queryClient';
import { cn } from '@/utils/cn';

interface GeneralSettings {
  siteName: string;
  siteUrl: string;
  timezone: string;
  dateFormat: string;
  language: string;
  maintenanceMode: boolean;
}

interface EmailSettings {
  smtpHost: string;
  smtpPort: string;
  smtpUser: string;
  smtpPassword: string;
  smtpFrom: string;
  smtpSecure: boolean;
}

interface NotificationSettings {
  emailAlerts: boolean;
  slackIntegration: boolean;
  webhookUrl: string;
  alertThreshold: 'all' | 'warning' | 'critical';
}

interface AppearanceSettings {
  defaultTheme: 'light' | 'dark' | 'system';
  primaryColor: string;
  logoUrl: string;
  faviconUrl: string;
  customCss: string;
}

interface DatabaseSettings {
  connectionStatus: 'connected' | 'disconnected' | 'error';
  host: string;
  port: string;
  database: string;
  poolSize: number;
  lastBackup: string | null;
  autoBackup: boolean;
  backupFrequency: 'daily' | 'weekly' | 'monthly';
}

interface SecuritySettings {
  sessionTimeout: number;
  maxLoginAttempts: number;
  passwordMinLength: number;
  requireUppercase: boolean;
  requireNumbers: boolean;
  requireSpecialChars: boolean;
  twoFactorRequired: boolean;
  ipWhitelist: string;
  auditLogRetention: number;
}

export const SystemSettings: React.FC = () => {
  const { success: showSuccess, error: showError } = useToastHelpers();
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState('general');
  const [showSmtpPassword, setShowSmtpPassword] = useState(false);

  // State for all settings sections
  const [generalSettings, setGeneralSettings] = useState<GeneralSettings>({
    siteName: 'Enterprise Dashboard',
    siteUrl: 'https://dashboard.example.com',
    timezone: 'UTC',
    dateFormat: 'YYYY-MM-DD',
    language: 'en',
    maintenanceMode: false,
  });

  const [emailSettings, setEmailSettings] = useState<EmailSettings>({
    smtpHost: 'smtp.example.com',
    smtpPort: '587',
    smtpUser: 'noreply@example.com',
    smtpPassword: '',
    smtpFrom: 'Enterprise Dashboard <noreply@example.com>',
    smtpSecure: true,
  });

  const [notificationSettings, setNotificationSettings] = useState<NotificationSettings>({
    emailAlerts: true,
    slackIntegration: false,
    webhookUrl: '',
    alertThreshold: 'warning',
  });

  const [appearanceSettings, setAppearanceSettings] = useState<AppearanceSettings>({
    defaultTheme: 'system',
    primaryColor: '#3b82f6',
    logoUrl: '',
    faviconUrl: '',
    customCss: '',
  });

  const [databaseSettings, setDatabaseSettings] = useState<DatabaseSettings>({
    connectionStatus: 'connected',
    host: 'localhost',
    port: '5432',
    database: 'enterprise_dashboard',
    poolSize: 10,
    lastBackup: null,
    autoBackup: true,
    backupFrequency: 'daily',
  });

  const [securitySettings, setSecuritySettings] = useState<SecuritySettings>({
    sessionTimeout: 30,
    maxLoginAttempts: 5,
    passwordMinLength: 8,
    requireUppercase: true,
    requireNumbers: true,
    requireSpecialChars: false,
    twoFactorRequired: false,
    ipWhitelist: '',
    auditLogRetention: 90,
  });

  // Fetch system settings
  const { data: settingsData, isLoading } = useQuery({
    queryKey: queryKeys.settings.system(),
    queryFn: settingsApi.getSystemSettings,
    staleTime: 5 * 60 * 1000,
  });

  // Initialize form with fetched data
  useEffect(() => {
    if (settingsData?.data) {
      const data = settingsData.data;
      if (data.general) setGeneralSettings(prev => ({ ...prev, ...data.general }));
      if (data.email) setEmailSettings(prev => ({ ...prev, ...data.email }));
      if (data.notifications) setNotificationSettings(prev => ({ ...prev, ...data.notifications }));
      if (data.appearance) setAppearanceSettings(prev => ({ ...prev, ...data.appearance }));
      if (data.database) setDatabaseSettings(prev => ({ ...prev, ...data.database }));
      if (data.security) setSecuritySettings(prev => ({ ...prev, ...data.security }));
    }
  }, [settingsData]);

  // Save settings mutation
  const saveMutation = useMutation({
    mutationFn: () => settingsApi.updateSystemSettings({
      general: generalSettings,
      email: emailSettings,
      notifications: notificationSettings,
      appearance: appearanceSettings,
      security: securitySettings,
    }),
    onSuccess: () => {
      showSuccess('Settings saved successfully');
      queryClient.invalidateQueries({ queryKey: queryKeys.settings.system() });
    },
    onError: () => {
      showError('Failed to save settings');
    },
  });

  // Test email mutation
  const testEmailMutation = useMutation({
    mutationFn: () => settingsApi.testEmail(),
    onSuccess: () => {
      showSuccess('Test email sent successfully');
    },
    onError: () => {
      showError('Failed to send test email');
    },
  });

  const handleSave = () => {
    saveMutation.mutate();
  };

  const handleTestEmail = () => {
    testEmailMutation.mutate();
  };

  const tabs = [
    { id: 'general', label: 'General', icon: Globe },
    { id: 'email', label: 'Email', icon: Mail },
    { id: 'notifications', label: 'Notifications', icon: Bell },
    { id: 'appearance', label: 'Appearance', icon: Palette },
    { id: 'database', label: 'Database', icon: Database },
    { id: 'security', label: 'Security', icon: Shield },
  ];

  const colorOptions = [
    { value: '#3b82f6', label: 'Blue' },
    { value: '#8b5cf6', label: 'Purple' },
    { value: '#22c55e', label: 'Green' },
    { value: '#f97316', label: 'Orange' },
    { value: '#ef4444', label: 'Red' },
    { value: '#6366f1', label: 'Indigo' },
  ];

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-8 w-8 animate-spin text-blue-500" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">System Settings</h1>
          <p className="text-gray-600">Configure global application settings</p>
        </div>
        <Button onClick={handleSave} disabled={saveMutation.isPending}>
          {saveMutation.isPending ? (
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          ) : (
            <Save className="mr-2 h-4 w-4" />
          )}
          {saveMutation.isPending ? 'Saving...' : 'Save Changes'}
        </Button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        {/* Sidebar */}
        <Card className="p-4 h-fit">
          <nav className="space-y-1">
            {tabs.map((tab) => {
              const Icon = tab.icon;
              return (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id)}
                  className={cn(
                    'w-full flex items-center px-3 py-2 text-sm font-medium rounded-md transition-colors',
                    activeTab === tab.id
                      ? 'bg-blue-50 text-blue-700'
                      : 'text-gray-600 hover:bg-gray-50'
                  )}
                >
                  <Icon className="mr-3 h-5 w-5" />
                  {tab.label}
                </button>
              );
            })}
          </nav>
        </Card>

        {/* Content */}
        <div className="lg:col-span-3">
          {/* General Settings */}
          {activeTab === 'general' && (
            <Card className="p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-6">General Settings</h2>
              <div className="space-y-4">
                <div>
                  <Label>Site Name</Label>
                  <Input
                    value={generalSettings.siteName}
                    onChange={(e) => setGeneralSettings({ ...generalSettings, siteName: e.target.value })}
                    className="mt-1"
                  />
                </div>
                <div>
                  <Label>Site URL</Label>
                  <Input
                    type="url"
                    value={generalSettings.siteUrl}
                    onChange={(e) => setGeneralSettings({ ...generalSettings, siteUrl: e.target.value })}
                    className="mt-1"
                  />
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <Label>Timezone</Label>
                    <select
                      value={generalSettings.timezone}
                      onChange={(e) => setGeneralSettings({ ...generalSettings, timezone: e.target.value })}
                      className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md"
                    >
                      <option value="UTC">UTC</option>
                      <option value="America/New_York">Eastern Time</option>
                      <option value="America/Chicago">Central Time</option>
                      <option value="America/Denver">Mountain Time</option>
                      <option value="America/Los_Angeles">Pacific Time</option>
                      <option value="Europe/London">London</option>
                      <option value="Europe/Paris">Paris</option>
                      <option value="Asia/Tokyo">Tokyo</option>
                    </select>
                  </div>
                  <div>
                    <Label>Date Format</Label>
                    <select
                      value={generalSettings.dateFormat}
                      onChange={(e) => setGeneralSettings({ ...generalSettings, dateFormat: e.target.value })}
                      className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md"
                    >
                      <option value="YYYY-MM-DD">2024-01-15</option>
                      <option value="MM/DD/YYYY">01/15/2024</option>
                      <option value="DD/MM/YYYY">15/01/2024</option>
                      <option value="DD.MM.YYYY">15.01.2024</option>
                    </select>
                  </div>
                </div>
                <div>
                  <Label>Default Language</Label>
                  <select
                    value={generalSettings.language}
                    onChange={(e) => setGeneralSettings({ ...generalSettings, language: e.target.value })}
                    className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md"
                  >
                    <option value="en">English</option>
                    <option value="es">Spanish</option>
                    <option value="fr">French</option>
                    <option value="de">German</option>
                    <option value="zh">Chinese</option>
                    <option value="ja">Japanese</option>
                  </select>
                </div>
                <div className="flex items-center justify-between py-3 border-t">
                  <div>
                    <p className="font-medium text-gray-900">Maintenance Mode</p>
                    <p className="text-sm text-gray-500">Temporarily disable access for non-admin users</p>
                  </div>
                  <button
                    onClick={() => setGeneralSettings({ ...generalSettings, maintenanceMode: !generalSettings.maintenanceMode })}
                    className={cn(
                      'relative w-11 h-6 rounded-full transition-colors',
                      generalSettings.maintenanceMode ? 'bg-orange-500' : 'bg-gray-200'
                    )}
                  >
                    <span className={cn(
                      'absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full transition-transform',
                      generalSettings.maintenanceMode && 'translate-x-5'
                    )} />
                  </button>
                </div>
                {generalSettings.maintenanceMode && (
                  <div className="bg-orange-50 border border-orange-200 rounded-lg p-4">
                    <div className="flex items-center">
                      <AlertTriangle className="h-5 w-5 text-orange-500 mr-2" />
                      <span className="text-sm text-orange-700">
                        Maintenance mode is enabled. Only admins can access the application.
                      </span>
                    </div>
                  </div>
                )}
              </div>
            </Card>
          )}

          {/* Email Settings */}
          {activeTab === 'email' && (
            <Card className="p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-6">Email Settings</h2>
              <div className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <Label>SMTP Host</Label>
                    <Input
                      value={emailSettings.smtpHost}
                      onChange={(e) => setEmailSettings({ ...emailSettings, smtpHost: e.target.value })}
                      className="mt-1"
                      placeholder="smtp.example.com"
                    />
                  </div>
                  <div>
                    <Label>SMTP Port</Label>
                    <Input
                      value={emailSettings.smtpPort}
                      onChange={(e) => setEmailSettings({ ...emailSettings, smtpPort: e.target.value })}
                      className="mt-1"
                      placeholder="587"
                    />
                  </div>
                </div>
                <div>
                  <Label>SMTP Username</Label>
                  <Input
                    value={emailSettings.smtpUser}
                    onChange={(e) => setEmailSettings({ ...emailSettings, smtpUser: e.target.value })}
                    className="mt-1"
                    placeholder="noreply@example.com"
                  />
                </div>
                <div>
                  <Label>SMTP Password</Label>
                  <div className="relative mt-1">
                    <Input
                      type={showSmtpPassword ? 'text' : 'password'}
                      value={emailSettings.smtpPassword}
                      onChange={(e) => setEmailSettings({ ...emailSettings, smtpPassword: e.target.value })}
                      placeholder="Enter SMTP password"
                    />
                    <button
                      type="button"
                      onClick={() => setShowSmtpPassword(!showSmtpPassword)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                    >
                      {showSmtpPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                </div>
                <div>
                  <Label>From Address</Label>
                  <Input
                    value={emailSettings.smtpFrom}
                    onChange={(e) => setEmailSettings({ ...emailSettings, smtpFrom: e.target.value })}
                    className="mt-1"
                    placeholder="Enterprise Dashboard <noreply@example.com>"
                  />
                </div>
                <div className="flex items-center justify-between py-3 border-t">
                  <div>
                    <p className="font-medium text-gray-900">Use TLS/SSL</p>
                    <p className="text-sm text-gray-500">Encrypt email communications</p>
                  </div>
                  <button
                    onClick={() => setEmailSettings({ ...emailSettings, smtpSecure: !emailSettings.smtpSecure })}
                    className={cn(
                      'relative w-11 h-6 rounded-full transition-colors',
                      emailSettings.smtpSecure ? 'bg-blue-600' : 'bg-gray-200'
                    )}
                  >
                    <span className={cn(
                      'absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full transition-transform',
                      emailSettings.smtpSecure && 'translate-x-5'
                    )} />
                  </button>
                </div>
                <div className="pt-4">
                  <Button
                    variant="outline"
                    onClick={handleTestEmail}
                    disabled={testEmailMutation.isPending}
                  >
                    {testEmailMutation.isPending ? (
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    ) : (
                      <Mail className="mr-2 h-4 w-4" />
                    )}
                    Send Test Email
                  </Button>
                </div>
              </div>
            </Card>
          )}

          {/* Notifications Settings */}
          {activeTab === 'notifications' && (
            <Card className="p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-6">Notification Settings</h2>
              <div className="space-y-4">
                <div className="flex items-center justify-between py-3 border-b">
                  <div>
                    <p className="font-medium text-gray-900">Email Alerts</p>
                    <p className="text-sm text-gray-500">Send system alerts via email</p>
                  </div>
                  <button
                    onClick={() => setNotificationSettings({ ...notificationSettings, emailAlerts: !notificationSettings.emailAlerts })}
                    className={cn(
                      'relative w-11 h-6 rounded-full transition-colors',
                      notificationSettings.emailAlerts ? 'bg-blue-600' : 'bg-gray-200'
                    )}
                  >
                    <span className={cn(
                      'absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full transition-transform',
                      notificationSettings.emailAlerts && 'translate-x-5'
                    )} />
                  </button>
                </div>
                <div className="flex items-center justify-between py-3 border-b">
                  <div>
                    <p className="font-medium text-gray-900">Slack Integration</p>
                    <p className="text-sm text-gray-500">Send notifications to Slack</p>
                  </div>
                  <button
                    onClick={() => setNotificationSettings({ ...notificationSettings, slackIntegration: !notificationSettings.slackIntegration })}
                    className={cn(
                      'relative w-11 h-6 rounded-full transition-colors',
                      notificationSettings.slackIntegration ? 'bg-blue-600' : 'bg-gray-200'
                    )}
                  >
                    <span className={cn(
                      'absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full transition-transform',
                      notificationSettings.slackIntegration && 'translate-x-5'
                    )} />
                  </button>
                </div>
                <div>
                  <Label>Webhook URL</Label>
                  <Input
                    type="url"
                    value={notificationSettings.webhookUrl}
                    onChange={(e) => setNotificationSettings({ ...notificationSettings, webhookUrl: e.target.value })}
                    className="mt-1"
                    placeholder="https://hooks.slack.com/services/..."
                  />
                  <p className="mt-1 text-xs text-gray-500">
                    Enter your Slack webhook URL for integration
                  </p>
                </div>
                <div>
                  <Label>Alert Threshold</Label>
                  <select
                    value={notificationSettings.alertThreshold}
                    onChange={(e) => setNotificationSettings({ ...notificationSettings, alertThreshold: e.target.value as 'all' | 'warning' | 'critical' })}
                    className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md"
                  >
                    <option value="all">All alerts</option>
                    <option value="warning">Warnings and critical only</option>
                    <option value="critical">Critical only</option>
                  </select>
                </div>
              </div>
            </Card>
          )}

          {/* Appearance Settings */}
          {activeTab === 'appearance' && (
            <Card className="p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-6">Appearance Settings</h2>
              <div className="space-y-6">
                <div>
                  <Label className="mb-3 block">Default Theme</Label>
                  <div className="grid grid-cols-3 gap-3">
                    {[
                      { value: 'light', label: 'Light', icon: Sun },
                      { value: 'dark', label: 'Dark', icon: Moon },
                      { value: 'system', label: 'System', icon: Monitor },
                    ].map(({ value, label, icon: Icon }) => (
                      <button
                        key={value}
                        onClick={() => setAppearanceSettings({ ...appearanceSettings, defaultTheme: value as 'light' | 'dark' | 'system' })}
                        className={cn(
                          'p-4 rounded-lg border-2 flex flex-col items-center gap-2 transition-colors',
                          appearanceSettings.defaultTheme === value
                            ? 'border-blue-500 bg-blue-50'
                            : 'border-gray-200 hover:border-gray-300'
                        )}
                      >
                        <Icon className="h-6 w-6" />
                        <span className="text-sm font-medium">{label}</span>
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <Label className="mb-3 block">Primary Color</Label>
                  <div className="flex gap-2">
                    {colorOptions.map(({ value, label }) => (
                      <button
                        key={value}
                        onClick={() => setAppearanceSettings({ ...appearanceSettings, primaryColor: value })}
                        className={cn(
                          'w-10 h-10 rounded-full border-2 transition-transform hover:scale-110',
                          appearanceSettings.primaryColor === value
                            ? 'border-gray-900 ring-2 ring-offset-2 ring-gray-300'
                            : 'border-white'
                        )}
                        style={{ backgroundColor: value }}
                        title={label}
                      />
                    ))}
                  </div>
                </div>

                <div>
                  <Label>Logo URL</Label>
                  <Input
                    type="url"
                    value={appearanceSettings.logoUrl}
                    onChange={(e) => setAppearanceSettings({ ...appearanceSettings, logoUrl: e.target.value })}
                    className="mt-1"
                    placeholder="https://example.com/logo.png"
                  />
                  <p className="mt-1 text-xs text-gray-500">
                    Recommended size: 200x50 pixels (PNG or SVG)
                  </p>
                </div>

                <div>
                  <Label>Favicon URL</Label>
                  <Input
                    type="url"
                    value={appearanceSettings.faviconUrl}
                    onChange={(e) => setAppearanceSettings({ ...appearanceSettings, faviconUrl: e.target.value })}
                    className="mt-1"
                    placeholder="https://example.com/favicon.ico"
                  />
                </div>

                <div>
                  <Label>Custom CSS</Label>
                  <textarea
                    value={appearanceSettings.customCss}
                    onChange={(e) => setAppearanceSettings({ ...appearanceSettings, customCss: e.target.value })}
                    className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md font-mono text-sm"
                    rows={6}
                    placeholder="/* Add custom CSS here */"
                  />
                  <p className="mt-1 text-xs text-gray-500">
                    Add custom CSS to override default styles
                  </p>
                </div>
              </div>
            </Card>
          )}

          {/* Database Settings */}
          {activeTab === 'database' && (
            <Card className="p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-6">Database Settings</h2>
              <div className="space-y-6">
                {/* Connection Status */}
                <div className="p-4 bg-gray-50 rounded-lg">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center">
                      {databaseSettings.connectionStatus === 'connected' ? (
                        <CheckCircle className="h-5 w-5 text-green-500 mr-2" />
                      ) : databaseSettings.connectionStatus === 'error' ? (
                        <XCircle className="h-5 w-5 text-red-500 mr-2" />
                      ) : (
                        <AlertTriangle className="h-5 w-5 text-yellow-500 mr-2" />
                      )}
                      <div>
                        <p className="font-medium text-gray-900">Connection Status</p>
                        <p className="text-sm text-gray-500 capitalize">{databaseSettings.connectionStatus}</p>
                      </div>
                    </div>
                    <Badge
                      variant={databaseSettings.connectionStatus === 'connected' ? 'default' : 'destructive'}
                    >
                      {databaseSettings.connectionStatus === 'connected' ? 'Healthy' : 'Issue'}
                    </Badge>
                  </div>
                </div>

                {/* Database Info */}
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <Label>Host</Label>
                    <Input value={databaseSettings.host} disabled className="mt-1 bg-gray-50" />
                  </div>
                  <div>
                    <Label>Port</Label>
                    <Input value={databaseSettings.port} disabled className="mt-1 bg-gray-50" />
                  </div>
                </div>

                <div>
                  <Label>Database Name</Label>
                  <Input value={databaseSettings.database} disabled className="mt-1 bg-gray-50" />
                </div>

                <div>
                  <Label>Connection Pool Size</Label>
                  <Input
                    type="number"
                    min={1}
                    max={100}
                    value={databaseSettings.poolSize}
                    onChange={(e) => setDatabaseSettings({ ...databaseSettings, poolSize: parseInt(e.target.value) || 10 })}
                    className="mt-1"
                  />
                  <p className="mt-1 text-xs text-gray-500">
                    Maximum number of database connections in the pool
                  </p>
                </div>

                {/* Backup Settings */}
                <div className="pt-4 border-t">
                  <h3 className="text-md font-medium text-gray-900 mb-4">Backup Configuration</h3>

                  <div className="flex items-center justify-between py-3">
                    <div>
                      <p className="font-medium text-gray-900">Automatic Backups</p>
                      <p className="text-sm text-gray-500">Enable scheduled database backups</p>
                    </div>
                    <button
                      onClick={() => setDatabaseSettings({ ...databaseSettings, autoBackup: !databaseSettings.autoBackup })}
                      className={cn(
                        'relative w-11 h-6 rounded-full transition-colors',
                        databaseSettings.autoBackup ? 'bg-blue-600' : 'bg-gray-200'
                      )}
                    >
                      <span className={cn(
                        'absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full transition-transform',
                        databaseSettings.autoBackup && 'translate-x-5'
                      )} />
                    </button>
                  </div>

                  {databaseSettings.autoBackup && (
                    <div className="mt-4">
                      <Label>Backup Frequency</Label>
                      <select
                        value={databaseSettings.backupFrequency}
                        onChange={(e) => setDatabaseSettings({ ...databaseSettings, backupFrequency: e.target.value as 'daily' | 'weekly' | 'monthly' })}
                        className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md"
                      >
                        <option value="daily">Daily</option>
                        <option value="weekly">Weekly</option>
                        <option value="monthly">Monthly</option>
                      </select>
                    </div>
                  )}

                  <div className="mt-4 p-4 bg-gray-50 rounded-lg">
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="text-sm font-medium text-gray-900">Last Backup</p>
                        <p className="text-sm text-gray-500">
                          {databaseSettings.lastBackup
                            ? new Date(databaseSettings.lastBackup).toLocaleString()
                            : 'No backup recorded'}
                        </p>
                      </div>
                      <Button variant="outline" size="sm">
                        <RefreshCw className="mr-2 h-4 w-4" />
                        Backup Now
                      </Button>
                    </div>
                  </div>
                </div>
              </div>
            </Card>
          )}

          {/* Security Settings */}
          {activeTab === 'security' && (
            <Card className="p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-6">Security Settings</h2>
              <div className="space-y-6">
                {/* Session Settings */}
                <div>
                  <h3 className="text-md font-medium text-gray-900 mb-4 flex items-center">
                    <Lock className="mr-2 h-4 w-4" />
                    Session Configuration
                  </h3>
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <Label>Session Timeout (minutes)</Label>
                      <Input
                        type="number"
                        min={5}
                        max={1440}
                        value={securitySettings.sessionTimeout}
                        onChange={(e) => setSecuritySettings({ ...securitySettings, sessionTimeout: parseInt(e.target.value) || 30 })}
                        className="mt-1"
                      />
                    </div>
                    <div>
                      <Label>Max Login Attempts</Label>
                      <Input
                        type="number"
                        min={3}
                        max={10}
                        value={securitySettings.maxLoginAttempts}
                        onChange={(e) => setSecuritySettings({ ...securitySettings, maxLoginAttempts: parseInt(e.target.value) || 5 })}
                        className="mt-1"
                      />
                    </div>
                  </div>
                </div>

                {/* Password Policy */}
                <div className="pt-4 border-t">
                  <h3 className="text-md font-medium text-gray-900 mb-4 flex items-center">
                    <Key className="mr-2 h-4 w-4" />
                    Password Policy
                  </h3>
                  <div className="space-y-4">
                    <div>
                      <Label>Minimum Password Length</Label>
                      <Input
                        type="number"
                        min={6}
                        max={32}
                        value={securitySettings.passwordMinLength}
                        onChange={(e) => setSecuritySettings({ ...securitySettings, passwordMinLength: parseInt(e.target.value) || 8 })}
                        className="mt-1"
                      />
                    </div>
                    <div className="space-y-3">
                      <div className="flex items-center justify-between py-2">
                        <span className="text-sm text-gray-700">Require uppercase letters</span>
                        <button
                          onClick={() => setSecuritySettings({ ...securitySettings, requireUppercase: !securitySettings.requireUppercase })}
                          className={cn(
                            'relative w-11 h-6 rounded-full transition-colors',
                            securitySettings.requireUppercase ? 'bg-blue-600' : 'bg-gray-200'
                          )}
                        >
                          <span className={cn(
                            'absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full transition-transform',
                            securitySettings.requireUppercase && 'translate-x-5'
                          )} />
                        </button>
                      </div>
                      <div className="flex items-center justify-between py-2">
                        <span className="text-sm text-gray-700">Require numbers</span>
                        <button
                          onClick={() => setSecuritySettings({ ...securitySettings, requireNumbers: !securitySettings.requireNumbers })}
                          className={cn(
                            'relative w-11 h-6 rounded-full transition-colors',
                            securitySettings.requireNumbers ? 'bg-blue-600' : 'bg-gray-200'
                          )}
                        >
                          <span className={cn(
                            'absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full transition-transform',
                            securitySettings.requireNumbers && 'translate-x-5'
                          )} />
                        </button>
                      </div>
                      <div className="flex items-center justify-between py-2">
                        <span className="text-sm text-gray-700">Require special characters</span>
                        <button
                          onClick={() => setSecuritySettings({ ...securitySettings, requireSpecialChars: !securitySettings.requireSpecialChars })}
                          className={cn(
                            'relative w-11 h-6 rounded-full transition-colors',
                            securitySettings.requireSpecialChars ? 'bg-blue-600' : 'bg-gray-200'
                          )}
                        >
                          <span className={cn(
                            'absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full transition-transform',
                            securitySettings.requireSpecialChars && 'translate-x-5'
                          )} />
                        </button>
                      </div>
                    </div>
                  </div>
                </div>

                {/* Two-Factor Authentication */}
                <div className="pt-4 border-t">
                  <div className="flex items-center justify-between py-3">
                    <div>
                      <p className="font-medium text-gray-900">Require Two-Factor Authentication</p>
                      <p className="text-sm text-gray-500">Force all users to enable 2FA</p>
                    </div>
                    <button
                      onClick={() => setSecuritySettings({ ...securitySettings, twoFactorRequired: !securitySettings.twoFactorRequired })}
                      className={cn(
                        'relative w-11 h-6 rounded-full transition-colors',
                        securitySettings.twoFactorRequired ? 'bg-blue-600' : 'bg-gray-200'
                      )}
                    >
                      <span className={cn(
                        'absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full transition-transform',
                        securitySettings.twoFactorRequired && 'translate-x-5'
                      )} />
                    </button>
                  </div>
                </div>

                {/* IP Whitelist */}
                <div className="pt-4 border-t">
                  <h3 className="text-md font-medium text-gray-900 mb-4">IP Whitelist</h3>
                  <div>
                    <Label>Allowed IP Addresses</Label>
                    <textarea
                      value={securitySettings.ipWhitelist}
                      onChange={(e) => setSecuritySettings({ ...securitySettings, ipWhitelist: e.target.value })}
                      className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md font-mono text-sm"
                      rows={4}
                      placeholder="192.168.1.0/24&#10;10.0.0.1&#10;# Add one IP or CIDR per line"
                    />
                    <p className="mt-1 text-xs text-gray-500">
                      Leave empty to allow all IPs. Use CIDR notation for ranges.
                    </p>
                  </div>
                </div>

                {/* Audit Logs */}
                <div className="pt-4 border-t">
                  <h3 className="text-md font-medium text-gray-900 mb-4">Audit Logging</h3>
                  <div>
                    <Label>Log Retention Period (days)</Label>
                    <Input
                      type="number"
                      min={30}
                      max={365}
                      value={securitySettings.auditLogRetention}
                      onChange={(e) => setSecuritySettings({ ...securitySettings, auditLogRetention: parseInt(e.target.value) || 90 })}
                      className="mt-1"
                    />
                    <p className="mt-1 text-xs text-gray-500">
                      Number of days to retain audit log entries
                    </p>
                  </div>
                </div>
              </div>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
};

export default SystemSettings;
