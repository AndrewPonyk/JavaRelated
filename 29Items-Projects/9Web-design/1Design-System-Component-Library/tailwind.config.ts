import type { Config } from 'tailwindcss';
import theme from './src/tokens/tailwind-theme.json';

const config: Config = {
  content: ['./public/**/*.html', './src/**/*.{ts,html}', './.storybook/**/*.{ts,html}'],
  theme: {
    extend: {
      colors: theme.colors,
      borderRadius: theme.borderRadius,
      spacing: theme.spacing,
      boxShadow: theme.boxShadow,
      zIndex: theme.zIndex
    }
  },
  plugins: []
};

export default config;
