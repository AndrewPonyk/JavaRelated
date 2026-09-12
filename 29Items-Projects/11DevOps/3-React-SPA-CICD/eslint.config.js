// ESLint 9 flat config — plugins must support flat config (docs/TECH-NOTES.md §3.6).
import js from '@eslint/js';
import prettier from 'eslint-config-prettier';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  {
    ignores: [
      'dist',
      'coverage',
      'playwright-report',
      'test-results',
      'node_modules',
      'public/mockServiceWorker.js',
      '.lighthouseci',
    ],
  },

  js.configs.recommended,
  ...tseslint.configs.recommended,

  // Application + mock code (browser)
  {
    files: ['src/**/*.{ts,tsx}', 'mocks/**/*.ts'],
    plugins: {
      react,
      'react-hooks': reactHooks,
      'jsx-a11y': jsxA11y,
    },
    languageOptions: {
      globals: { ...globals.browser },
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    settings: { react: { version: 'detect' } },
    rules: {
      ...react.configs.flat.recommended.rules,
      ...react.configs.flat['jsx-runtime'].rules,
      ...reactHooks.configs.recommended.rules,
      ...jsxA11y.flatConfigs.recommended.rules,
      'react/prop-types': 'off', // TypeScript owns prop contracts
      'react/no-unescaped-entities': 'off', // apostrophes in copy are fine
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      '@typescript-eslint/consistent-type-imports': 'error',
      'no-console': 'error', // use src/lib/logger.ts instead
    },
  },

  // Jest tests + test infrastructure
  {
    files: ['src/**/*.test.{ts,tsx}', 'src/test/**/*.{ts,tsx}'],
    languageOptions: {
      globals: { ...globals.jest, ...globals.node, ...globals.browser },
    },
    rules: {
      'no-console': 'off',
    },
  },

  // Node-side code: configs, scripts, Playwright
  {
    files: ['e2e/**/*.ts', 'scripts/**', '*.config.{js,ts}', 'jest.config.ts'],
    languageOptions: {
      globals: { ...globals.node },
    },
    rules: {
      'no-console': 'off',
    },
  },

  // Must come last: disables stylistic rules that would fight Prettier.
  prettier,
);
