/// <reference types="vite/client" />

export {};

declare global {
  interface Window {
    __APP_CONFIG__?: {
      GRAPHQL_ENDPOINT?: string;
    };
  }
}
