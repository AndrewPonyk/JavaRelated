/**
 * In-memory "database" behind the MSW handlers — this repo's stand-in for a schema
 * (the real database belongs to the API service; see docs/PROJECT-PLAN.md §2.2).
 * Shapes mirror contracts/openapi.yaml.
 */

export interface DbUser {
  id: string;
  email: string;
  password: string; // mock-only, obviously
  displayName: string;
  roles: Array<'customer' | 'admin'>;
}

export interface DbProfile {
  displayName: string;
  marketingEmails: boolean;
  productUpdates: boolean;
}

export interface DbAnnouncement {
  id: string;
  title: string;
  body: string;
  publishedAt: string;
}

export interface DbDashboardSummary {
  balance: number;
  currency: string;
  openTickets: number;
  lastLoginAt: string | null;
  /** Month-end balances, oldest first — feeds the dashboard trend sparkline. */
  balanceHistory: Array<{ month: string; balance: number }>;
}

export interface DbActivityEntry {
  id: string;
  date: string;
  description: string;
  /** Positive = credit, negative = debit. */
  amount: number;
  currency: string;
}

/** Credentials used across unit tests, E2E fixtures, and the docs. */
export const DEMO_USER = {
  email: 'demo@example.com',
  password: 'Password123!',
} as const;

interface Db {
  users: DbUser[];
  profile: DbProfile;
  announcements: DbAnnouncement[];
  dashboardSummary: DbDashboardSummary;
  activity: DbActivityEntry[];
  /** Access tokens issued by the mock login endpoint. */
  validTokens: Set<string>;
  /**
   * Models the server-side refresh session (the real API tracks this via the httpOnly
   * refresh cookie). True between login and logout; the refresh endpoint honors it.
   */
  sessionActive: boolean;
}

function seed(): Db {
  return {
    users: [
      {
        id: 'user-1',
        email: DEMO_USER.email,
        password: DEMO_USER.password,
        displayName: 'Demo Customer',
        roles: ['customer'],
      },
    ],
    profile: {
      displayName: 'Demo Customer',
      marketingEmails: false,
      productUpdates: true,
    },
    announcements: [
      {
        id: 'ann-1',
        title: 'Scheduled maintenance',
        body: 'The portal will be briefly unavailable on Sunday 02:00–02:30 UTC.',
        publishedAt: '2026-07-01T09:00:00.000Z',
      },
      {
        id: 'ann-2',
        title: 'New: download statements as PDF',
        body: 'You can now export account statements from the dashboard.',
        publishedAt: '2026-06-15T12:00:00.000Z',
      },
    ],
    dashboardSummary: {
      balance: 2417.53,
      currency: 'EUR',
      openTickets: 1,
      lastLoginAt: '2026-07-08T14:32:00.000Z',
      balanceHistory: [
        { month: '2026-02', balance: 1802.1 },
        { month: '2026-03', balance: 1954.4 },
        { month: '2026-04', balance: 1721.9 },
        { month: '2026-05', balance: 2110.35 },
        { month: '2026-06', balance: 2338.0 },
        { month: '2026-07', balance: 2417.53 },
      ],
    },
    activity: [
      {
        id: 'act-1',
        date: '2026-07-10T08:15:00.000Z',
        description: 'Monthly subscription payment',
        amount: -29.99,
        currency: 'EUR',
      },
      {
        id: 'act-2',
        date: '2026-07-05T16:40:00.000Z',
        description: 'Refund — support ticket #4821',
        amount: 12.5,
        currency: 'EUR',
      },
      {
        id: 'act-3',
        date: '2026-07-01T09:00:00.000Z',
        description: 'Account top-up',
        amount: 250.0,
        currency: 'EUR',
      },
    ],
    validTokens: new Set(),
    sessionActive: false,
  };
}

export let db: Db = seed();

/** Restore pristine seed data — called between Jest tests (src/test/setupTests.ts). */
export function resetDb(): void {
  db = seed();
}
