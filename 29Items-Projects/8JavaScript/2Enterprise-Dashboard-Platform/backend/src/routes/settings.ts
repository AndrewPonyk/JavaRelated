import { Router, Request, Response } from 'express';
import { z } from 'zod';
import { prisma } from '@/config/database.js';
import { extendedCacheService as cacheService } from '@/services/cache/cacheService.js';
import { authenticate, authorize } from '@/middleware/auth/authMiddleware.js';
import { csrfProtection } from '@/middleware/security/csrfMiddleware.js';
import { asyncHandler } from '@/utils/asyncHandler.js';
import { logger, logBusinessEvent } from '@/utils/logger.js';
import { ValidationError, AuthorizationError, NotFoundError } from '@/utils/errors.js';

const router = Router();

// Request with user
interface AuthenticatedRequest extends Request {
  user?: {
    id: string;
    email: string;
    role: string;
  };
}

// Apply authentication to all routes
router.use(authenticate);

// User settings validation schema
const userSettingsSchema = z.object({
  theme: z.enum(['light', 'dark', 'auto']).optional(),
  timezone: z.string().optional(),
  language: z.string().min(2).max(5).optional(),
  notifications: z.object({
    email: z.boolean().optional(),
    push: z.boolean().optional(),
    dashboard: z.boolean().optional(),
    security: z.boolean().optional(),
    updates: z.boolean().optional()
  }).optional(),
  displayDensity: z.enum(['compact', 'comfortable', 'spacious']).optional(),
  dateFormat: z.string().optional(),
  defaultDashboard: z.string().optional()
});

// System settings validation schema (admin only)
const systemSettingsSchema = z.object({
  general: z.object({
    siteName: z.string().min(1).max(100).optional(),
    siteDescription: z.string().max(500).optional(),
    maintenanceMode: z.boolean().optional(),
    allowRegistration: z.boolean().optional(),
    defaultUserRole: z.enum(['USER', 'VIEWER']).optional(),
    sessionTimeout: z.number().min(5).max(1440).optional() // minutes
  }).optional(),
  email: z.object({
    smtpHost: z.string().optional(),
    smtpPort: z.number().optional(),
    smtpUser: z.string().optional(),
    smtpFrom: z.string().email().optional(),
    enableEmailNotifications: z.boolean().optional()
  }).optional(),
  security: z.object({
    passwordMinLength: z.number().min(6).max(128).optional(),
    requireUppercase: z.boolean().optional(),
    requireNumbers: z.boolean().optional(),
    requireSpecialChars: z.boolean().optional(),
    mfaEnabled: z.boolean().optional(),
    maxLoginAttempts: z.number().min(3).max(20).optional(),
    lockoutDuration: z.number().min(1).max(60).optional() // minutes
  }).optional(),
  appearance: z.object({
    primaryColor: z.string().optional(),
    logo: z.string().optional(),
    favicon: z.string().optional(),
    customCss: z.string().optional()
  }).optional(),
  features: z.object({
    enablePublicDashboards: z.boolean().optional(),
    enableExport: z.boolean().optional(),
    enableComments: z.boolean().optional(),
    enableVersioning: z.boolean().optional(),
    maxDashboardsPerUser: z.number().min(1).optional(),
    maxWidgetsPerDashboard: z.number().min(1).optional()
  }).optional()
});

// In-memory system settings (in production, use database)
let systemSettings: any = {
  general: {
    siteName: 'Enterprise Dashboard Platform',
    siteDescription: 'Advanced analytics and data visualization',
    maintenanceMode: false,
    allowRegistration: true,
    defaultUserRole: 'USER',
    sessionTimeout: 60
  },
  email: {
    smtpHost: '',
    smtpPort: 587,
    smtpUser: '',
    smtpFrom: 'noreply@example.com',
    enableEmailNotifications: true
  },
  security: {
    passwordMinLength: 8,
    requireUppercase: true,
    requireNumbers: true,
    requireSpecialChars: false,
    mfaEnabled: false,
    maxLoginAttempts: 5,
    lockoutDuration: 15
  },
  appearance: {
    primaryColor: '#3b82f6',
    logo: null,
    favicon: null,
    customCss: ''
  },
  features: {
    enablePublicDashboards: true,
    enableExport: true,
    enableComments: true,
    enableVersioning: true,
    maxDashboardsPerUser: 50,
    maxWidgetsPerDashboard: 100
  }
};

// ============================================
// User Settings Routes
// ============================================

/**
 * @route   GET /api/settings/user
 * @desc    Get current user's settings
 * @access  Private
 */
router.get('/user', asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user?.id;
  if (!userId) {
    throw new AuthorizationError('User not authenticated');
  }

  // Get user profile with settings
  const profile = await prisma.userProfile.findUnique({
    where: { userId }
  });

  if (!profile) {
    // Return defaults if no profile exists
    return res.json({
      success: true,
      data: {
        theme: 'light',
        timezone: 'UTC',
        language: 'en',
        notifications: {
          email: true,
          push: true,
          dashboard: true,
          security: true,
          updates: false
        },
        displayDensity: 'comfortable',
        dateFormat: 'MM/DD/YYYY',
        defaultDashboard: null
      },
      message: 'User settings retrieved successfully'
    });
  }

  // Parse notifications if stored as string
  let notifications = profile.notifications;
  if (typeof notifications === 'string') {
    try {
      notifications = JSON.parse(notifications);
    } catch {
      notifications = {};
    }
  }

  return res.json({
    success: true,
    data: {
      theme: profile.theme || 'light',
      timezone: profile.timezone || 'UTC',
      language: profile.language || 'en',
      notifications: notifications || {},
      avatar: profile.avatar,
      bio: profile.bio
    },
    message: 'User settings retrieved successfully'
  });
}));

/**
 * @route   PUT /api/settings/user
 * @desc    Update current user's settings
 * @access  Private
 */
router.put('/user', csrfProtection, asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user?.id;
  if (!userId) {
    throw new AuthorizationError('User not authenticated');
  }

  const data = userSettingsSchema.parse(req.body);

  // Prepare update data
  const updateData: any = {};
  if (data.theme) updateData.theme = data.theme;
  if (data.timezone) updateData.timezone = data.timezone;
  if (data.language) updateData.language = data.language;
  if (data.notifications) updateData.notifications = JSON.stringify(data.notifications);

  // Upsert profile
  const profile = await prisma.userProfile.upsert({
    where: { userId },
    create: {
      userId,
      theme: data.theme || 'light',
      timezone: data.timezone || 'UTC',
      language: data.language || 'en',
      notifications: JSON.stringify(data.notifications || {})
    },
    update: updateData
  });

  // Invalidate user cache
  await cacheService.invalidateUser(userId);

  // Log activity
  await prisma.activityLog.create({
    data: {
      action: 'SETTINGS_UPDATED',
      entity: 'user',
      entityId: userId,
      userId,
      details: JSON.stringify({ changes: Object.keys(data) })
    }
  });

  logBusinessEvent('USER_SETTINGS_UPDATED', {
    userId,
    changes: Object.keys(data)
  });

  logger.info('User settings updated', { userId, changes: Object.keys(data) });

  res.json({
    success: true,
    data: {
      theme: profile.theme,
      timezone: profile.timezone,
      language: profile.language,
      notifications: typeof profile.notifications === 'string'
        ? JSON.parse(profile.notifications)
        : profile.notifications
    },
    message: 'Settings updated successfully'
  });
}));

// ============================================
// System Settings Routes (Admin only)
// ============================================

/**
 * @route   GET /api/settings/system
 * @desc    Get system settings
 * @access  Private (Admin only)
 */
router.get('/system', authorize('SUPER_ADMIN', 'ADMIN'), asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user?.id;

  logger.info('System settings retrieved', { userId });

  // Don't expose sensitive data like SMTP password
  const safeSettings = {
    ...systemSettings,
    email: {
      ...systemSettings.email,
      smtpPassword: systemSettings.email?.smtpPassword ? '********' : undefined
    }
  };

  res.json({
    success: true,
    data: safeSettings,
    message: 'System settings retrieved successfully'
  });
}));

/**
 * @route   PUT /api/settings/system
 * @desc    Update system settings
 * @access  Private (Admin only)
 */
router.put('/system', authorize('SUPER_ADMIN', 'ADMIN'), csrfProtection, asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user?.id;
  if (!userId) {
    throw new AuthorizationError('User not authenticated');
  }

  const data = systemSettingsSchema.parse(req.body);

  // Update system settings
  if (data.general) {
    systemSettings.general = { ...systemSettings.general, ...data.general };
  }
  if (data.email) {
    systemSettings.email = { ...systemSettings.email, ...data.email };
  }
  if (data.security) {
    systemSettings.security = { ...systemSettings.security, ...data.security };
  }
  if (data.appearance) {
    systemSettings.appearance = { ...systemSettings.appearance, ...data.appearance };
  }
  if (data.features) {
    systemSettings.features = { ...systemSettings.features, ...data.features };
  }

  // Log activity
  await prisma.activityLog.create({
    data: {
      action: 'SYSTEM_SETTINGS_UPDATED',
      entity: 'system',
      entityId: 'settings',
      userId,
      details: JSON.stringify({ sections: Object.keys(data) })
    }
  });

  logBusinessEvent('SYSTEM_SETTINGS_UPDATED', {
    userId,
    sections: Object.keys(data)
  });

  logger.info('System settings updated', { userId, sections: Object.keys(data) });

  res.json({
    success: true,
    data: systemSettings,
    message: 'System settings updated successfully'
  });
}));

/**
 * @route   GET /api/settings/system/:section
 * @desc    Get specific section of system settings
 * @access  Private (Admin only)
 */
router.get('/system/:section', authorize('SUPER_ADMIN', 'ADMIN'), asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
  const section = req.params.section as string;

  if (!section || !systemSettings[section]) {
    throw new NotFoundError(`Settings section "${section}"`);
  }

  res.json({
    success: true,
    data: systemSettings[section],
    message: `${section} settings retrieved successfully`
  });
}));

/**
 * @route   PUT /api/settings/system/:section
 * @desc    Update specific section of system settings
 * @access  Private (Admin only)
 */
router.put('/system/:section', authorize('SUPER_ADMIN', 'ADMIN'), csrfProtection, asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user?.id;
  const section = req.params.section as string;

  if (!userId) {
    throw new AuthorizationError('User not authenticated');
  }

  if (!section || !systemSettings[section]) {
    throw new NotFoundError(`Settings section "${section}"`);
  }

  // Update the specific section
  systemSettings[section] = { ...systemSettings[section], ...req.body };

  // Log activity
  await prisma.activityLog.create({
    data: {
      action: 'SYSTEM_SETTINGS_SECTION_UPDATED',
      entity: 'system',
      entityId: section,
      userId,
      details: JSON.stringify({ section, changes: Object.keys(req.body) })
    }
  });

  logger.info('System settings section updated', { userId, section });

  res.json({
    success: true,
    data: systemSettings[section],
    message: `${section} settings updated successfully`
  });
}));

/**
 * @route   POST /api/settings/system/test-email
 * @desc    Test email configuration
 * @access  Private (Admin only)
 */
router.post('/system/test-email', authorize('SUPER_ADMIN', 'ADMIN'), csrfProtection, asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user?.id;
  const { recipient } = req.body;

  if (!recipient) {
    throw new ValidationError('Recipient email is required');
  }

  // In production, this would actually send a test email
  logger.info('Test email requested', { userId, recipient });

  // Simulate email sending
  const success = systemSettings.email?.smtpHost ? true : false;

  if (!success) {
    return res.status(400).json({
      success: false,
      message: 'SMTP configuration is incomplete'
    });
  }

  return res.json({
    success: true,
    message: `Test email sent to ${recipient}`
  });
}));

/**
 * @route   POST /api/settings/system/reset
 * @desc    Reset system settings to defaults
 * @access  Private (Super Admin only)
 */
router.post('/system/reset', authorize('SUPER_ADMIN'), csrfProtection, asyncHandler(async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user?.id;
  const { section } = req.body;

  if (!userId) {
    throw new AuthorizationError('User not authenticated');
  }

  const defaults: any = {
    general: {
      siteName: 'Enterprise Dashboard Platform',
      siteDescription: 'Advanced analytics and data visualization',
      maintenanceMode: false,
      allowRegistration: true,
      defaultUserRole: 'USER',
      sessionTimeout: 60
    },
    email: {
      smtpHost: '',
      smtpPort: 587,
      smtpUser: '',
      smtpFrom: 'noreply@example.com',
      enableEmailNotifications: true
    },
    security: {
      passwordMinLength: 8,
      requireUppercase: true,
      requireNumbers: true,
      requireSpecialChars: false,
      mfaEnabled: false,
      maxLoginAttempts: 5,
      lockoutDuration: 15
    },
    appearance: {
      primaryColor: '#3b82f6',
      logo: null,
      favicon: null,
      customCss: ''
    },
    features: {
      enablePublicDashboards: true,
      enableExport: true,
      enableComments: true,
      enableVersioning: true,
      maxDashboardsPerUser: 50,
      maxWidgetsPerDashboard: 100
    }
  };

  if (section && defaults[section]) {
    systemSettings[section] = defaults[section];
  } else {
    systemSettings = { ...defaults };
  }

  // Log activity
  await prisma.activityLog.create({
    data: {
      action: 'SYSTEM_SETTINGS_RESET',
      entity: 'system',
      entityId: section || 'all',
      userId,
      details: JSON.stringify({ section: section || 'all' })
    }
  });

  logger.info('System settings reset', { userId, section: section || 'all' });

  res.json({
    success: true,
    data: section ? systemSettings[section] : systemSettings,
    message: `${section || 'All'} settings reset to defaults`
  });
}));

export default router;
