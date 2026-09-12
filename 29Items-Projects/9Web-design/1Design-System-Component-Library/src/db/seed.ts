import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { getPool, closePool } from './connection';
import { PgTokenRepository } from './token.repository';
import { TokenService } from '../services/token.service';
import type { TokenCategory } from '../services/token-models';

interface RawTokenLeaf {
  value: string;
  description?: string;
}

function isTokenLeaf(value: unknown): value is RawTokenLeaf {
  return Boolean(value && typeof value === 'object' && 'value' in value);
}

function flattenTokens(source: Record<string, unknown>): Array<{
  name: string;
  category: TokenCategory;
  value: string;
  description?: string;
}> {
  const output: Array<{
    name: string;
    category: TokenCategory;
    value: string;
    description?: string;
  }> = [];

  function normalizeSegment(segment: string): string {
    return segment.replace(/[A-Z]/g, (match) => `-${match.toLowerCase()}`).toLowerCase();
  }

  function visit(category: TokenCategory, path: string[], node: unknown): void {
    if (isTokenLeaf(node)) {
      output.push({
        name: [normalizeSegment(category), ...path.map(normalizeSegment)].join('.'),
        category,
        value: String(node.value),
        description: node.description
      });
      return;
    }

    if (node && typeof node === 'object') {
      for (const [key, value] of Object.entries(node)) {
        visit(category, [...path, key], value);
      }
    }
  }

  for (const [category, value] of Object.entries(source)) {
    visit(category as TokenCategory, [], value);
  }

  return output;
}

export async function seedDatabase(): Promise<void> {
  const raw = await readFile(resolve(process.cwd(), 'src/tokens/design-tokens.json'), 'utf8');
  const tokenSource = JSON.parse(raw) as Record<string, unknown>;
  const repository = new PgTokenRepository(getPool());
  const service = new TokenService(repository);

  const existingSet = await repository.getTokenSetByName('core');
  const tokenSet =
    existingSet ??
    (await service.createTokenSet(
      {
        name: 'core',
        description: 'Canonical design-system token set seeded from source control',
        source: 'src/tokens/design-tokens.json'
      },
      'seed'
    ));

  for (const token of flattenTokens(tokenSource)) {
    const existingToken = await repository.getTokenByName(tokenSet.id, token.name);
    if (!existingToken) {
      const created = await service.createToken({
        tokenSetId: tokenSet.id,
        name: token.name,
        category: token.category,
        value: token.value,
        description: token.description,
        createdBy: 'seed',
        changeNote: 'Seeded from token source'
      });
      await service.approveToken(created.id, 'seed', 'Approved seeded token');
    }
  }
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  seedDatabase()
    .catch((error) => {
      console.error(error);
      process.exitCode = 1;
    })
    .finally(() => {
      void closePool();
    });
}
