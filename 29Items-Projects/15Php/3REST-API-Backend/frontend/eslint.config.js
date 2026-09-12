import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'

// Flat config (ESLint 9). Vue 3 recommended + JS recommended.
export default [
  // Never lint build output or deps (these are minified/generated).
  { ignores: ['dist/**', 'node_modules/**'] },
  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        localStorage: 'readonly',
        setTimeout: 'readonly',
        Intl: 'readonly',
        console: 'readonly',
      },
    },
    rules: {
      'vue/multi-word-component-names': 'off',
      // Purely-stylistic whitespace rules — these are a formatter's job
      // (Prettier), so we disable them here to keep lint signal meaningful.
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/multiline-html-element-content-newline': 'off',
      'vue/html-self-closing': 'off',
      'vue/first-attribute-linebreak': 'off',
    },
  },
]
