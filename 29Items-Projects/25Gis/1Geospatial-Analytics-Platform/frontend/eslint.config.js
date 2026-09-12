import js from "@eslint/js";
import tseslint from "typescript-eslint";

export default [
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ["src/**/*.{ts,tsx}"],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: "module",
      parserOptions: {
        ecmaFeatures: {
          jsx: true,
        },
      },
      globals: {
        AbortSignal: "readonly",
        DOMException: "readonly",
        fetch: "readonly",
        console: "readonly",
        document: "readonly",
      },
    },
    rules: {
      "no-unused-vars": "warn",
    },
  },
];
