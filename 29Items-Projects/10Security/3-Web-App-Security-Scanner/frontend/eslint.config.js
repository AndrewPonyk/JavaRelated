import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'

// Flat config (ESLint 9). `flat/essential` = correctness rules only —
// template line-wrapping stays a non-issue for review.
export default [
  {
    ignores: ['dist/**', 'node_modules/**', 'tests/**'],
  },
  js.configs.recommended,
  ...pluginVue.configs['flat/essential'],
  {
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        window: 'readonly',
        document: 'readonly',
        localStorage: 'readonly',
        URL: 'readonly',
        fetch: 'readonly',
        console: 'readonly',
      },
    },
    rules: {
      'no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
    },
  },
]
