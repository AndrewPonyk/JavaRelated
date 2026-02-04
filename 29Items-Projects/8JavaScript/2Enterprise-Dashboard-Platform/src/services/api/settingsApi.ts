import { apiClient } from './apiClient';

// Types
export interface UserSettings {
  theme: 'light' | 'dark' | 'auto';
  timezone: string;
  language: string;
  notifications: {
    email?: boolean;
    push?: boolean;
    dashboard?: boolean;
    security?: boolean;
    updates?: boolean;
  };
  avatar?: string;
  bio?: string;
  displayDensity?: 'compact' | 'comfortable' | 'spacious';
  dateFormat?: string;
  defaultDashboard?: string;
}

export interface SystemSettings {
  general: {
    siteName: string;
    siteDescription: string;
    maintenanceMode: boolean;
    allowRegistration: boolean;
    defaultUserRole: 'USER' | 'VIEWER';
    sessionTimeout: number;
  };
  email: {
    smtpHost: string;
    smtpPort: number;
    smtpUser: string;
    smtpFrom: string;
    enableEmailNotifications: boolean;
  };
  security: {
    passwordMinLength: number;
    requireUppercase: boolean;
    requireNumbers: boolean;
    requireSpecialChars: boolean;
    mfaEnabled: boolean;
    maxLoginAttempts: number;
    lockoutDuration: number;
  };
  appearance: {
    primaryColor: string;
    logo?: string;
    favicon?: string;
    customCss?: string;
  };
  features: {
    enablePublicDashboards: boolean;
    enableExport: boolean;
    enableComments: boolean;
    enableVersioning: boolean;
    maxDashboardsPerUser: number;
    maxWidgetsPerDashboard: number;
  };
}

// API functions
export const settingsApi = {
  // User settings
  getUserSettings: async (): Promise<{ success: boolean; data: UserSettings }> => {
    const response = await apiClient.get('/settings/user');
    return response.data;
  },

  updateUserSettings: async (settings: Partial<UserSettings>): Promise<{ success: boolean; data: UserSettings }> => {
    const response = await apiClient.put('/settings/user', settings);
    return response.data;
  },

  // System settings (admin only)
  getSystemSettings: async (): Promise<{ success: boolean; data: SystemSettings }> => {
    const response = await apiClient.get('/settings/system');
    return response.data;
  },

  updateSystemSettings: async (settings: Partial<SystemSettings>): Promise<{ success: boolean; data: SystemSettings }> => {
    const response = await apiClient.put('/settings/system', settings);
    return response.data;
  },

  // Get specific section
  getSystemSettingsSection: async <K extends keyof SystemSettings>(section: K): Promise<{ success: boolean; data: SystemSettings[K] }> => {
    const response = await apiClient.get(`/settings/system/${section}`);
    return response.data;
  },

  // Update specific section
  updateSystemSettingsSection: async <K extends keyof SystemSettings>(
    section: K,
    settings: Partial<SystemSettings[K]>
  ): Promise<{ success: boolean; data: SystemSettings[K] }> => {
    const response = await apiClient.put(`/settings/system/${section}`, settings);
    return response.data;
  },

  // Test email
  testEmail: async (recipient: string): Promise<{ success: boolean; message: string }> => {
    const response = await apiClient.post('/settings/system/test-email', { recipient });
    return response.data;
  },

  // Reset settings
  resetSystemSettings: async (section?: keyof SystemSettings): Promise<{ success: boolean; data: any; message: string }> => {
    const response = await apiClient.post('/settings/system/reset', { section });
    return response.data;
  },
};

export default settingsApi;
