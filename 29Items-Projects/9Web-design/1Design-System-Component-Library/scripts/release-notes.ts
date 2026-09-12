import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { flattenTokenObject } from './token-utils';

export async function generateReleaseNotes(): Promise<void> {
  const packageJson = JSON.parse(
    await readFile(resolve(process.cwd(), 'package.json'), 'utf8')
  ) as {
    version: string;
  };
  const tokens = flattenTokenObject(
    JSON.parse(
      await readFile(resolve(process.cwd(), 'src/tokens/design-tokens.json'), 'utf8')
    ) as Record<string, unknown>
  );

  const notes = [
    `# Release ${packageJson.version}`,
    '',
    '## Design Tokens',
    '',
    ...tokens.map((token) => `- ${token.name}: ${token.value}`),
    '',
    '## Verification',
    '',
    '- Run `npm run format`',
    '- Run `npm run lint`',
    '- Run `npm test`',
    '- Run `npm run build`',
    '- Run `npm run build:storybook`'
  ].join('\n');

  await writeFile(resolve(process.cwd(), 'docs/RELEASE-NOTES.md'), `${notes}\n`, 'utf8');
}

generateReleaseNotes().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
