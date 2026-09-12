import { authHandlers } from './auth.handlers';
import { dashboardHandlers } from './dashboard.handlers';
import { settingsHandlers } from './settings.handlers';

/** One handler set serves dev (browser.ts), Jest (server.ts), and hermetic E2E builds. */
export const handlers = [...authHandlers, ...dashboardHandlers, ...settingsHandlers];
