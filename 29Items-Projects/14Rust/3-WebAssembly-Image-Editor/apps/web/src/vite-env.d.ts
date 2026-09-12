/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_ENABLE_PRESET_API?: string;
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_MAX_IMAGE_PIXELS?: string;
  readonly VITE_MAX_IMAGE_BYTES?: string;
  readonly VITE_MODEL_MANIFEST_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
