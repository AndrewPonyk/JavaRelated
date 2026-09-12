import { env } from '@/app/env';

/**
 * The only sanctioned logging surface — `no-console` is an ESLint error elsewhere.
 * Centralizing gives one place for levels, environment gating, PII scrubbing, and
 * error-report forwarding (docs/ARCHITECTURE.md §2.6).
 */
export type LogLevel = 'debug' | 'info' | 'warn' | 'error';

const LEVEL_ORDER: Record<LogLevel, number> = { debug: 10, info: 20, warn: 30, error: 40 };

// Local dev is chatty; production only surfaces what matters.
const threshold: number = env.isProd ? LEVEL_ORDER.warn : LEVEL_ORDER.debug;

export type LogContext = Record<string, unknown>;
export type ErrorTransport = (
  level: 'warn' | 'error',
  message: string,
  context?: LogContext,
) => void;

/**
 * Pluggable forwarding for warn/error in production — register Sentry (or any collector)
 * here at bootstrap, with release = env.appVersion for sourcemapped traces. Scrub PII in
 * the transport, at the boundary, before anything leaves the browser.
 */
let errorTransport: ErrorTransport | null = null;

export function registerErrorTransport(transport: ErrorTransport | null): void {
  errorTransport = transport;
}

function log(level: LogLevel, message: string, context?: LogContext): void {
  if (LEVEL_ORDER[level] < threshold) return;

  const prefix = `[portal:${env.envName}]`;
  // eslint-disable-next-line no-console -- the logger is the one allowed console user
  console[level](prefix, message, context ?? '');

  if (errorTransport && (level === 'error' || level === 'warn')) {
    try {
      errorTransport(level, message, context);
    } catch {
      // a broken transport must never take the app down with it
    }
  }
}

export const logger = {
  debug: (message: string, context?: LogContext) => log('debug', message, context),
  info: (message: string, context?: LogContext) => log('info', message, context),
  warn: (message: string, context?: LogContext) => log('warn', message, context),
  error: (message: string, context?: LogContext) => log('error', message, context),
};
