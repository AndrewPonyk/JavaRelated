import type { Preview } from '@storybook/web-components';
import '../src/styles/main.scss';
import '../src/index';

const preview: Preview = {
  parameters: {
    a11y: {
      config: {
        rules: [
          {
            id: 'color-contrast',
            enabled: true
          }
        ]
      }
    },
    controls: {
      expanded: true
    }
  }
};

export default preview;
