/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** API origin/prefix. "/api" (same-origin) unless pointing at a remote preview. */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
