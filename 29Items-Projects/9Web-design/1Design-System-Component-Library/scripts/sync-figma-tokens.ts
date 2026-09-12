import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

interface FigmaFileResponse {
  name?: string;
  document?: unknown;
}

function normalizeTokens(input: unknown): Record<string, unknown> {
  if (!input || typeof input !== 'object') {
    throw new Error('Token input must be a JSON object');
  }

  const record = input as Record<string, unknown>;
  if ('tokens' in record && record.tokens && typeof record.tokens === 'object') {
    return record.tokens as Record<string, unknown>;
  }

  return record;
}

async function readLocalTokenExport(path: string): Promise<Record<string, unknown>> {
  return normalizeTokens(JSON.parse(await readFile(resolve(process.cwd(), path), 'utf8')));
}

async function readFigmaFileSummary(
  figmaToken: string,
  figmaFileKey: string
): Promise<Record<string, unknown>> {
  const response = await fetch(`https://api.figma.com/v1/files/${figmaFileKey}`, {
    headers: {
      'X-Figma-Token': figmaToken
    }
  });

  if (!response.ok) {
    throw new Error(`Figma API request failed with ${response.status}`);
  }

  const figmaFile = (await response.json()) as FigmaFileResponse;
  await writeFile(
    resolve(process.cwd(), 'src/tokens/figma-sync-manifest.json'),
    `${JSON.stringify(
      {
        figmaFileKey,
        figmaFileName: figmaFile.name ?? figmaFileKey,
        syncedAt: new Date().toISOString()
      },
      null,
      2
    )}\n`,
    'utf8'
  );
  return readLocalTokenExport('src/tokens/design-tokens.json');
}

async function main(): Promise<void> {
  const localExport = process.env.FIGMA_TOKENS_FILE;
  const figmaToken = process.env.FIGMA_TOKEN;
  const figmaFileKey = process.env.FIGMA_FILE_KEY;

  const tokens = localExport
    ? await readLocalTokenExport(localExport)
    : figmaToken && figmaFileKey
      ? await readFigmaFileSummary(figmaToken, figmaFileKey)
      : await readLocalTokenExport('src/tokens/design-tokens.json');

  await writeFile(
    resolve(process.cwd(), 'src/tokens/design-tokens.json'),
    `${JSON.stringify(normalizeTokens(tokens), null, 2)}\n`,
    'utf8'
  );
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
