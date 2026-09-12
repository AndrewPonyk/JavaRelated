/// <reference types="vite/client" />

// Typed environment contract — mirrors .env.example. Only src/app/env.ts may read these
// (docs/TECH-NOTES.md §3.6 #1).
interface ImportMetaEnv {
  readonly VITE_ENV_NAME?: 'local' | 'e2e' | 'preview' | 'staging' | 'production';
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_ENABLE_MSW?: string;
  readonly VITE_GA_MEASUREMENT_ID?: string;
  readonly VITE_APP_VERSION?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
