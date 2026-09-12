import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { cssVariableName, flattenTokenObject } from './token-utils';

function buildScss(tokens: ReturnType<typeof flattenTokenObject>): string {
  const customProperties = tokens
    .map((token) => `  ${cssVariableName(token.name)}: ${token.value};`)
    .join('\n');
  const sassVariables = tokens
    .map(
      (token) =>
        `$ds-${token.name.replace(/[A-Z]/g, (match) => `-${match.toLowerCase()}`).replace(/\./g, '-')}: var(${cssVariableName(token.name)});`
    )
    .join('\n');

  return `:root {\n${customProperties}\n}\n\n${sassVariables}\n`;
}

function buildTailwindTheme(
  tokens: ReturnType<typeof flattenTokenObject>
): Record<string, unknown> {
  const colors: Record<string, string> = {};
  const spacing: Record<string, string> = {};
  const borderRadius: Record<string, string> = {};
  const boxShadow: Record<string, string> = {};
  const zIndex: Record<string, string> = {};

  for (const token of tokens) {
    const key = token.path.join('-');
    const value = `var(${cssVariableName(token.name)})`;

    if (token.category === 'color') {
      colors[key] = value;
    }
    if (token.category === 'spacing') {
      spacing[key] = value;
    }
    if (token.category === 'radius') {
      borderRadius[key] = value;
    }
    if (token.category === 'shadow') {
      boxShadow[key] = value;
    }
    if (token.category === 'zIndex') {
      zIndex[key] = value;
    }
  }

  return { colors, spacing, borderRadius, boxShadow, zIndex };
}

export async function generateTokenArtifacts(): Promise<void> {
  const sourcePath = resolve(process.cwd(), 'src/tokens/design-tokens.json');
  const source = JSON.parse(await readFile(sourcePath, 'utf8')) as Record<string, unknown>;
  const tokens = flattenTokenObject(source);

  await writeFile(resolve(process.cwd(), 'src/styles/tokens.scss'), buildScss(tokens), 'utf8');
  await writeFile(
    resolve(process.cwd(), 'src/tokens/tailwind-theme.json'),
    `${JSON.stringify(buildTailwindTheme(tokens), null, 2)}\n`,
    'utf8'
  );
}

if ((process.argv[1] ?? '').replace(/\\/g, '/').endsWith('/generate-token-artifacts.ts')) {
  generateTokenArtifacts().catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
}
