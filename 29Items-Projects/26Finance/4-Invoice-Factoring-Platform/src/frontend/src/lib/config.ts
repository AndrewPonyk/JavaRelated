/**
 * Frontend configuration sourced from Vite env vars. Only `VITE_`-prefixed values are
 * exposed to the browser bundle — never put server secrets here (TECH-NOTES §3.6).
 */
export const config = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
  stripePublishableKey: import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY ?? '',
  plaidEnv: import.meta.env.VITE_PLAID_ENV ?? 'sandbox',
} as const;
