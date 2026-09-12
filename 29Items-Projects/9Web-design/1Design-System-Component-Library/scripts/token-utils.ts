export interface TokenLeaf {
  value: string;
  description?: string;
}

export interface FlattenedToken {
  name: string;
  category: string;
  path: string[];
  value: string;
  description?: string;
}

export function isTokenLeaf(value: unknown): value is TokenLeaf {
  return Boolean(value && typeof value === 'object' && 'value' in value);
}

export function flattenTokenObject(source: Record<string, unknown>): FlattenedToken[] {
  const output: FlattenedToken[] = [];

  function visit(category: string, path: string[], node: unknown): void {
    if (isTokenLeaf(node)) {
      output.push({
        name: [category, ...path].join('.'),
        category,
        path,
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
    visit(category, [], value);
  }

  return output.sort((a, b) => a.name.localeCompare(b.name));
}

export function cssVariableName(tokenName: string): string {
  return `--ds-${tokenName.replace(/[A-Z]/g, (match) => `-${match.toLowerCase()}`).replace(/\./g, '-')}`;
}
