import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { generateTokenArtifacts } from '../../scripts/generate-token-artifacts';

describe('token artifact generation', () => {
  it('generates Sass variables and Tailwind theme output from the canonical token source', async () => {
    await generateTokenArtifacts();
    const scss = await readFile(resolve(process.cwd(), 'src/styles/tokens.scss'), 'utf8');
    const tailwind = JSON.parse(
      await readFile(resolve(process.cwd(), 'src/tokens/tailwind-theme.json'), 'utf8')
    ) as {
      colors: Record<string, string>;
      spacing: Record<string, string>;
    };

    expect(scss).toContain('--ds-color-brand-primary: #265CFF;');
    expect(scss).toContain('$ds-spacing-component: var(--ds-spacing-component);');
    expect(tailwind.colors['brand-primary']).toBe('var(--ds-color-brand-primary)');
    expect(tailwind.spacing.component).toBe('var(--ds-spacing-component)');
  });
});
