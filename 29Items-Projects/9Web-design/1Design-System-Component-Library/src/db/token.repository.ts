import { randomUUID } from 'node:crypto';
import type pg from 'pg';
import type {
  CreateTokenInput,
  CreateTokenSetInput,
  DesignToken,
  TokenAuditEvent,
  TokenCategory,
  TokenChangelogEntry,
  TokenFilters,
  TokenSet,
  TokenStatus,
  TokenVersion,
  UpdateTokenInput,
  UpdateTokenSetInput
} from '../services/token-models';

export interface TokenRepository {
  listTokenSets(): Promise<TokenSet[]>;
  getTokenSet(id: string): Promise<TokenSet | undefined>;
  getTokenSetByName(name: string): Promise<TokenSet | undefined>;
  createTokenSet(input: Required<CreateTokenSetInput>): Promise<TokenSet>;
  updateTokenSet(id: string, input: UpdateTokenSetInput): Promise<TokenSet | undefined>;
  deleteTokenSet(id: string): Promise<boolean>;
  listTokens(filters?: TokenFilters): Promise<DesignToken[]>;
  getToken(id: string): Promise<DesignToken | undefined>;
  getTokenByName(tokenSetId: string, name: string): Promise<DesignToken | undefined>;
  createToken(
    input: CreateTokenInput & { status: TokenStatus; deprecated: boolean }
  ): Promise<DesignToken>;
  updateToken(id: string, input: UpdateTokenInput): Promise<DesignToken | undefined>;
  deleteToken(id: string): Promise<boolean>;
  createVersion(input: {
    tokenId: string;
    version: number;
    value: string;
    status: TokenStatus;
    changeNote?: string | null;
    createdBy?: string | null;
  }): Promise<TokenVersion>;
  listVersions(tokenId: string): Promise<TokenVersion[]>;
  nextVersionNumber(tokenId: string): Promise<number>;
  createAuditEvent(input: {
    tokenId?: string | null;
    tokenSetId?: string | null;
    eventType: string;
    eventPayload: Record<string, unknown>;
    createdBy?: string | null;
  }): Promise<TokenAuditEvent>;
  listAuditEvents(filters?: { tokenId?: string; tokenSetId?: string }): Promise<TokenAuditEvent[]>;
  listChangelog(tokenSetId?: string): Promise<TokenChangelogEntry[]>;
}

function now(): string {
  return new Date().toISOString();
}

function toTokenSet(row: Record<string, unknown>): TokenSet {
  return {
    id: String(row.id),
    name: String(row.name),
    description: (row.description as string | null) ?? null,
    source: String(row.source),
    createdAt: new Date(row.created_at as string).toISOString(),
    updatedAt: new Date(row.updated_at as string).toISOString()
  };
}

function toToken(row: Record<string, unknown>): DesignToken {
  return {
    id: String(row.id),
    tokenSetId: String(row.token_set_id),
    name: String(row.name),
    category: row.category as TokenCategory,
    value: String(row.value),
    description: (row.description as string | null) ?? null,
    status: row.status as TokenStatus,
    deprecated: Boolean(row.deprecated),
    figmaNodeId: (row.figma_node_id as string | null) ?? null,
    createdAt: new Date(row.created_at as string).toISOString(),
    updatedAt: new Date(row.updated_at as string).toISOString()
  };
}

function toVersion(row: Record<string, unknown>): TokenVersion {
  return {
    id: String(row.id),
    tokenId: String(row.token_id),
    version: Number(row.version),
    value: String(row.value),
    status: row.status as TokenStatus,
    changeNote: (row.change_note as string | null) ?? null,
    createdBy: (row.created_by as string | null) ?? null,
    createdAt: new Date(row.created_at as string).toISOString()
  };
}

function toAuditEvent(row: Record<string, unknown>): TokenAuditEvent {
  return {
    id: String(row.id),
    tokenId: (row.token_id as string | null) ?? null,
    tokenSetId: (row.token_set_id as string | null) ?? null,
    eventType: String(row.event_type),
    eventPayload: (row.event_payload as Record<string, unknown>) ?? {},
    createdBy: (row.created_by as string | null) ?? null,
    createdAt: new Date(row.created_at as string).toISOString()
  };
}

export class PgTokenRepository implements TokenRepository {
  constructor(private readonly pool: pg.Pool) {}

  async listTokenSets(): Promise<TokenSet[]> {
    const result = await this.pool.query('SELECT * FROM design_token_sets ORDER BY name ASC');
    return result.rows.map(toTokenSet);
  }

  async getTokenSet(id: string): Promise<TokenSet | undefined> {
    const result = await this.pool.query('SELECT * FROM design_token_sets WHERE id = $1', [id]);
    return result.rows[0] ? toTokenSet(result.rows[0]) : undefined;
  }

  async getTokenSetByName(name: string): Promise<TokenSet | undefined> {
    const result = await this.pool.query(
      'SELECT * FROM design_token_sets WHERE lower(name) = lower($1)',
      [name]
    );
    return result.rows[0] ? toTokenSet(result.rows[0]) : undefined;
  }

  async createTokenSet(input: Required<CreateTokenSetInput>): Promise<TokenSet> {
    const result = await this.pool.query(
      `INSERT INTO design_token_sets (name, description, source)
       VALUES ($1, $2, $3)
       RETURNING *`,
      [input.name, input.description, input.source]
    );
    return toTokenSet(result.rows[0]);
  }

  async updateTokenSet(id: string, input: UpdateTokenSetInput): Promise<TokenSet | undefined> {
    const existing = await this.getTokenSet(id);
    if (!existing) {
      return undefined;
    }

    const result = await this.pool.query(
      `UPDATE design_token_sets
       SET name = $2, description = $3, source = $4, updated_at = CURRENT_TIMESTAMP
       WHERE id = $1
       RETURNING *`,
      [
        id,
        input.name ?? existing.name,
        input.description === undefined ? existing.description : input.description,
        input.source ?? existing.source
      ]
    );
    return toTokenSet(result.rows[0]);
  }

  async deleteTokenSet(id: string): Promise<boolean> {
    const result = await this.pool.query('DELETE FROM design_token_sets WHERE id = $1', [id]);
    return (result.rowCount ?? 0) > 0;
  }

  async listTokens(filters: TokenFilters = {}): Promise<DesignToken[]> {
    const conditions: string[] = [];
    const values: unknown[] = [];

    if (filters.tokenSetId) {
      values.push(filters.tokenSetId);
      conditions.push(`token_set_id = $${values.length}`);
    }

    if (filters.category) {
      values.push(filters.category);
      conditions.push(`category = $${values.length}`);
    }

    if (filters.status) {
      values.push(filters.status);
      conditions.push(`status = $${values.length}`);
    }

    if (filters.search) {
      values.push(`%${filters.search}%`);
      conditions.push(`(name ILIKE $${values.length} OR description ILIKE $${values.length})`);
    }

    const where = conditions.length > 0 ? `WHERE ${conditions.join(' AND ')}` : '';
    const result = await this.pool.query(
      `SELECT * FROM design_tokens ${where} ORDER BY name ASC`,
      values
    );
    return result.rows.map(toToken);
  }

  async getToken(id: string): Promise<DesignToken | undefined> {
    const result = await this.pool.query('SELECT * FROM design_tokens WHERE id = $1', [id]);
    return result.rows[0] ? toToken(result.rows[0]) : undefined;
  }

  async getTokenByName(tokenSetId: string, name: string): Promise<DesignToken | undefined> {
    const result = await this.pool.query(
      'SELECT * FROM design_tokens WHERE token_set_id = $1 AND lower(name) = lower($2)',
      [tokenSetId, name]
    );
    return result.rows[0] ? toToken(result.rows[0]) : undefined;
  }

  async createToken(
    input: CreateTokenInput & { status: TokenStatus; deprecated: boolean }
  ): Promise<DesignToken> {
    const result = await this.pool.query(
      `INSERT INTO design_tokens
         (token_set_id, name, category, value, description, status, deprecated, figma_node_id)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
       RETURNING *`,
      [
        input.tokenSetId,
        input.name,
        input.category,
        input.value,
        input.description ?? null,
        input.status,
        input.deprecated,
        input.figmaNodeId ?? null
      ]
    );
    return toToken(result.rows[0]);
  }

  async updateToken(id: string, input: UpdateTokenInput): Promise<DesignToken | undefined> {
    const existing = await this.getToken(id);
    if (!existing) {
      return undefined;
    }

    const result = await this.pool.query(
      `UPDATE design_tokens
       SET name = $2,
           category = $3,
           value = $4,
           description = $5,
           status = $6,
           deprecated = $7,
           figma_node_id = $8,
           updated_at = CURRENT_TIMESTAMP
       WHERE id = $1
       RETURNING *`,
      [
        id,
        input.name ?? existing.name,
        input.category ?? existing.category,
        input.value ?? existing.value,
        input.description === undefined ? existing.description : input.description,
        input.status ?? existing.status,
        input.deprecated ?? existing.deprecated,
        input.figmaNodeId === undefined ? existing.figmaNodeId : input.figmaNodeId
      ]
    );
    return toToken(result.rows[0]);
  }

  async deleteToken(id: string): Promise<boolean> {
    const result = await this.pool.query('DELETE FROM design_tokens WHERE id = $1', [id]);
    return (result.rowCount ?? 0) > 0;
  }

  async createVersion(input: {
    tokenId: string;
    version: number;
    value: string;
    status: TokenStatus;
    changeNote?: string | null;
    createdBy?: string | null;
  }): Promise<TokenVersion> {
    const result = await this.pool.query(
      `INSERT INTO design_token_versions (token_id, version, value, status, change_note, created_by)
       VALUES ($1, $2, $3, $4, $5, $6)
       RETURNING *`,
      [
        input.tokenId,
        input.version,
        input.value,
        input.status,
        input.changeNote ?? null,
        input.createdBy ?? null
      ]
    );
    return toVersion(result.rows[0]);
  }

  async listVersions(tokenId: string): Promise<TokenVersion[]> {
    const result = await this.pool.query(
      'SELECT * FROM design_token_versions WHERE token_id = $1 ORDER BY version DESC',
      [tokenId]
    );
    return result.rows.map(toVersion);
  }

  async nextVersionNumber(tokenId: string): Promise<number> {
    const result = await this.pool.query(
      'SELECT COALESCE(MAX(version), 0) + 1 AS next_version FROM design_token_versions WHERE token_id = $1',
      [tokenId]
    );
    return Number(result.rows[0].next_version);
  }

  async createAuditEvent(input: {
    tokenId?: string | null;
    tokenSetId?: string | null;
    eventType: string;
    eventPayload: Record<string, unknown>;
    createdBy?: string | null;
  }): Promise<TokenAuditEvent> {
    const result = await this.pool.query(
      `INSERT INTO design_token_audit_events
         (token_id, token_set_id, event_type, event_payload, created_by)
       VALUES ($1, $2, $3, $4, $5)
       RETURNING *`,
      [
        input.tokenId ?? null,
        input.tokenSetId ?? null,
        input.eventType,
        JSON.stringify(input.eventPayload),
        input.createdBy ?? null
      ]
    );
    return toAuditEvent(result.rows[0]);
  }

  async listAuditEvents(
    filters: { tokenId?: string; tokenSetId?: string } = {}
  ): Promise<TokenAuditEvent[]> {
    const conditions: string[] = [];
    const values: string[] = [];

    if (filters.tokenId) {
      values.push(filters.tokenId);
      conditions.push(`token_id = $${values.length}`);
    }

    if (filters.tokenSetId) {
      values.push(filters.tokenSetId);
      conditions.push(`token_set_id = $${values.length}`);
    }

    const where = conditions.length > 0 ? `WHERE ${conditions.join(' AND ')}` : '';
    const result = await this.pool.query(
      `SELECT * FROM design_token_audit_events ${where} ORDER BY created_at DESC`,
      values
    );
    return result.rows.map(toAuditEvent);
  }

  async listChangelog(tokenSetId?: string): Promise<TokenChangelogEntry[]> {
    const values = tokenSetId ? [tokenSetId] : [];
    const where = tokenSetId ? 'WHERE t.token_set_id = $1' : '';
    const result = await this.pool.query(
      `SELECT t.name AS token_name,
              v.version,
              v.value,
              v.status,
              v.change_note,
              v.created_by,
              v.created_at
       FROM design_token_versions v
       JOIN design_tokens t ON t.id = v.token_id
       ${where}
       ORDER BY v.created_at DESC, t.name ASC`,
      values
    );

    return result.rows.map((row) => ({
      tokenName: String(row.token_name),
      version: Number(row.version),
      value: String(row.value),
      status: row.status as TokenStatus,
      changeNote: (row.change_note as string | null) ?? null,
      createdBy: (row.created_by as string | null) ?? null,
      createdAt: new Date(row.created_at as string).toISOString()
    }));
  }
}

export class MemoryTokenRepository implements TokenRepository {
  private readonly tokenSets = new Map<string, TokenSet>();
  private readonly tokens = new Map<string, DesignToken>();
  private readonly versions = new Map<string, TokenVersion[]>();
  private readonly auditEvents: TokenAuditEvent[] = [];

  async listTokenSets(): Promise<TokenSet[]> {
    return [...this.tokenSets.values()].sort((a, b) => a.name.localeCompare(b.name));
  }

  async getTokenSet(id: string): Promise<TokenSet | undefined> {
    return this.tokenSets.get(id);
  }

  async getTokenSetByName(name: string): Promise<TokenSet | undefined> {
    return [...this.tokenSets.values()].find(
      (set) => set.name.toLowerCase() === name.toLowerCase()
    );
  }

  async createTokenSet(input: Required<CreateTokenSetInput>): Promise<TokenSet> {
    const timestamp = now();
    const tokenSet: TokenSet = {
      id: randomUUID(),
      name: input.name,
      description: input.description,
      source: input.source,
      createdAt: timestamp,
      updatedAt: timestamp
    };
    this.tokenSets.set(tokenSet.id, tokenSet);
    return tokenSet;
  }

  async updateTokenSet(id: string, input: UpdateTokenSetInput): Promise<TokenSet | undefined> {
    const existing = this.tokenSets.get(id);
    if (!existing) {
      return undefined;
    }

    const updated: TokenSet = {
      ...existing,
      name: input.name ?? existing.name,
      description: input.description === undefined ? existing.description : input.description,
      source: input.source ?? existing.source,
      updatedAt: now()
    };
    this.tokenSets.set(id, updated);
    return updated;
  }

  async deleteTokenSet(id: string): Promise<boolean> {
    const deleted = this.tokenSets.delete(id);
    for (const token of [...this.tokens.values()]) {
      if (token.tokenSetId === id) {
        this.tokens.delete(token.id);
      }
    }
    return deleted;
  }

  async listTokens(filters: TokenFilters = {}): Promise<DesignToken[]> {
    return [...this.tokens.values()]
      .filter((token) => !filters.tokenSetId || token.tokenSetId === filters.tokenSetId)
      .filter((token) => !filters.category || token.category === filters.category)
      .filter((token) => !filters.status || token.status === filters.status)
      .filter(
        (token) =>
          !filters.search ||
          token.name.toLowerCase().includes(filters.search.toLowerCase()) ||
          (token.description ?? '').toLowerCase().includes(filters.search.toLowerCase())
      )
      .sort((a, b) => a.name.localeCompare(b.name));
  }

  async getToken(id: string): Promise<DesignToken | undefined> {
    return this.tokens.get(id);
  }

  async getTokenByName(tokenSetId: string, name: string): Promise<DesignToken | undefined> {
    return [...this.tokens.values()].find(
      (token) => token.tokenSetId === tokenSetId && token.name.toLowerCase() === name.toLowerCase()
    );
  }

  async createToken(
    input: CreateTokenInput & { status: TokenStatus; deprecated: boolean }
  ): Promise<DesignToken> {
    const timestamp = now();
    const token: DesignToken = {
      id: randomUUID(),
      tokenSetId: input.tokenSetId,
      name: input.name,
      category: input.category,
      value: input.value,
      description: input.description ?? null,
      status: input.status,
      deprecated: input.deprecated,
      figmaNodeId: input.figmaNodeId ?? null,
      createdAt: timestamp,
      updatedAt: timestamp
    };
    this.tokens.set(token.id, token);
    return token;
  }

  async updateToken(id: string, input: UpdateTokenInput): Promise<DesignToken | undefined> {
    const existing = this.tokens.get(id);
    if (!existing) {
      return undefined;
    }

    const updated: DesignToken = {
      ...existing,
      name: input.name ?? existing.name,
      category: input.category ?? existing.category,
      value: input.value ?? existing.value,
      description: input.description === undefined ? existing.description : input.description,
      status: input.status ?? existing.status,
      deprecated: input.deprecated ?? existing.deprecated,
      figmaNodeId: input.figmaNodeId === undefined ? existing.figmaNodeId : input.figmaNodeId,
      updatedAt: now()
    };
    this.tokens.set(id, updated);
    return updated;
  }

  async deleteToken(id: string): Promise<boolean> {
    return this.tokens.delete(id);
  }

  async createVersion(input: {
    tokenId: string;
    version: number;
    value: string;
    status: TokenStatus;
    changeNote?: string | null;
    createdBy?: string | null;
  }): Promise<TokenVersion> {
    const version: TokenVersion = {
      id: randomUUID(),
      tokenId: input.tokenId,
      version: input.version,
      value: input.value,
      status: input.status,
      changeNote: input.changeNote ?? null,
      createdBy: input.createdBy ?? null,
      createdAt: now()
    };
    const list = this.versions.get(input.tokenId) ?? [];
    list.push(version);
    this.versions.set(input.tokenId, list);
    return version;
  }

  async listVersions(tokenId: string): Promise<TokenVersion[]> {
    return [...(this.versions.get(tokenId) ?? [])].sort((a, b) => b.version - a.version);
  }

  async nextVersionNumber(tokenId: string): Promise<number> {
    const versions = this.versions.get(tokenId) ?? [];
    return versions.reduce((max, version) => Math.max(max, version.version), 0) + 1;
  }

  async createAuditEvent(input: {
    tokenId?: string | null;
    tokenSetId?: string | null;
    eventType: string;
    eventPayload: Record<string, unknown>;
    createdBy?: string | null;
  }): Promise<TokenAuditEvent> {
    const event: TokenAuditEvent = {
      id: randomUUID(),
      tokenId: input.tokenId ?? null,
      tokenSetId: input.tokenSetId ?? null,
      eventType: input.eventType,
      eventPayload: input.eventPayload,
      createdBy: input.createdBy ?? null,
      createdAt: now()
    };
    this.auditEvents.push(event);
    return event;
  }

  async listAuditEvents(
    filters: { tokenId?: string; tokenSetId?: string } = {}
  ): Promise<TokenAuditEvent[]> {
    return this.auditEvents
      .filter((event) => !filters.tokenId || event.tokenId === filters.tokenId)
      .filter((event) => !filters.tokenSetId || event.tokenSetId === filters.tokenSetId)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }

  async listChangelog(tokenSetId?: string): Promise<TokenChangelogEntry[]> {
    const tokensById = new Map([...this.tokens.values()].map((token) => [token.id, token]));
    return [...this.versions.values()]
      .flat()
      .map((version) => ({ version, token: tokensById.get(version.tokenId) }))
      .filter((entry): entry is { version: TokenVersion; token: DesignToken } =>
        Boolean(entry.token)
      )
      .filter((entry) => !tokenSetId || entry.token.tokenSetId === tokenSetId)
      .sort((a, b) => b.version.createdAt.localeCompare(a.version.createdAt))
      .map((entry) => ({
        tokenName: entry.token.name,
        version: entry.version.version,
        value: entry.version.value,
        status: entry.version.status,
        changeNote: entry.version.changeNote,
        createdBy: entry.version.createdBy,
        createdAt: entry.version.createdAt
      }));
  }
}
