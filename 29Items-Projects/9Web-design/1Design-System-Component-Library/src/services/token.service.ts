import type { TokenRepository } from '../db/token.repository';
import { DomainError } from './domain-errors';
import type {
  CreateTokenInput,
  CreateTokenSetInput,
  DesignToken,
  TokenAuditEvent,
  TokenChangelogEntry,
  TokenDiffEntry,
  TokenFilters,
  TokenSet,
  TokenStatus,
  TokenVersion,
  UpdateTokenInput,
  UpdateTokenSetInput
} from './token-models';

const tokenNamePattern = /^[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)+$/;
const tokenSetNamePattern = /^[a-z][a-z0-9-]{1,80}$/;

export class TokenService {
  constructor(private readonly repository: TokenRepository) {}

  async listTokenSets(): Promise<TokenSet[]> {
    return this.repository.listTokenSets();
  }

  async getTokenSet(id: string): Promise<TokenSet> {
    const tokenSet = await this.repository.getTokenSet(id);
    if (!tokenSet) {
      throw new DomainError('TOKEN_SET_NOT_FOUND', `Token set '${id}' was not found`, 404);
    }
    return tokenSet;
  }

  async createTokenSet(input: CreateTokenSetInput, actor = 'system'): Promise<TokenSet> {
    this.validateTokenSetName(input.name);
    const existing = await this.repository.getTokenSetByName(input.name);
    if (existing) {
      throw new DomainError('TOKEN_SET_EXISTS', `Token set '${input.name}' already exists`, 409);
    }

    const tokenSet = await this.repository.createTokenSet({
      name: input.name,
      description: input.description ?? '',
      source: input.source ?? 'local'
    });
    await this.repository.createAuditEvent({
      tokenSetId: tokenSet.id,
      eventType: 'token_set.created',
      eventPayload: { name: tokenSet.name, source: tokenSet.source },
      createdBy: actor
    });
    return tokenSet;
  }

  async updateTokenSet(
    id: string,
    input: UpdateTokenSetInput,
    actor = 'system'
  ): Promise<TokenSet> {
    if (input.name) {
      this.validateTokenSetName(input.name);
      const existingByName = await this.repository.getTokenSetByName(input.name);
      if (existingByName && existingByName.id !== id) {
        throw new DomainError('TOKEN_SET_EXISTS', `Token set '${input.name}' already exists`, 409);
      }
    }

    const updated = await this.repository.updateTokenSet(id, input);
    if (!updated) {
      throw new DomainError('TOKEN_SET_NOT_FOUND', `Token set '${id}' was not found`, 404);
    }

    await this.repository.createAuditEvent({
      tokenSetId: id,
      eventType: 'token_set.updated',
      eventPayload: input as Record<string, unknown>,
      createdBy: actor
    });
    return updated;
  }

  async deleteTokenSet(id: string, actor = 'system'): Promise<void> {
    const tokenSet = await this.getTokenSet(id);
    await this.repository.createAuditEvent({
      tokenSetId: tokenSet.id,
      eventType: 'token_set.deleted',
      eventPayload: { id: tokenSet.id, name: tokenSet.name },
      createdBy: actor
    });

    const deleted = await this.repository.deleteTokenSet(id);
    if (!deleted) {
      throw new DomainError('TOKEN_SET_NOT_FOUND', `Token set '${id}' was not found`, 404);
    }
  }

  async listTokens(filters: TokenFilters = {}): Promise<DesignToken[]> {
    return this.repository.listTokens(filters);
  }

  async getToken(idOrName: string, tokenSetId?: string): Promise<DesignToken> {
    const token = tokenSetId
      ? await this.repository.getTokenByName(tokenSetId, idOrName)
      : await this.repository.getToken(idOrName);

    if (!token) {
      throw new DomainError('TOKEN_NOT_FOUND', `Token '${idOrName}' was not found`, 404);
    }

    return token;
  }

  async createToken(input: CreateTokenInput): Promise<DesignToken> {
    await this.getTokenSet(input.tokenSetId);
    this.validateTokenName(input.name);
    this.validateTokenValue(input.value);

    const existing = await this.repository.getTokenByName(input.tokenSetId, input.name);
    if (existing) {
      throw new DomainError(
        'TOKEN_EXISTS',
        `Token '${input.name}' already exists in this token set`,
        409
      );
    }

    const token = await this.repository.createToken({
      ...input,
      description: input.description ?? null,
      figmaNodeId: input.figmaNodeId ?? null,
      status: 'draft',
      deprecated: false
    });

    await this.recordVersion(token, input.changeNote ?? 'Initial token version', input.createdBy);
    await this.repository.createAuditEvent({
      tokenId: token.id,
      tokenSetId: token.tokenSetId,
      eventType: 'token.created',
      eventPayload: { name: token.name, category: token.category, value: token.value },
      createdBy: input.createdBy ?? 'system'
    });

    return token;
  }

  async updateToken(id: string, input: UpdateTokenInput): Promise<DesignToken> {
    const existing = await this.getToken(id);

    if (input.name && input.name !== existing.name) {
      this.validateTokenName(input.name);
      const existingByName = await this.repository.getTokenByName(existing.tokenSetId, input.name);
      if (existingByName && existingByName.id !== id) {
        throw new DomainError(
          'TOKEN_EXISTS',
          `Token '${input.name}' already exists in this token set`,
          409
        );
      }
    }

    if (input.value !== undefined) {
      this.validateTokenValue(input.value);
    }

    const updated = await this.repository.updateToken(id, input);
    if (!updated) {
      throw new DomainError('TOKEN_NOT_FOUND', `Token '${id}' was not found`, 404);
    }

    const versionedFieldsChanged =
      updated.value !== existing.value ||
      updated.status !== existing.status ||
      updated.name !== existing.name ||
      updated.category !== existing.category ||
      updated.deprecated !== existing.deprecated;

    if (versionedFieldsChanged) {
      await this.recordVersion(updated, input.changeNote ?? 'Token updated', input.createdBy);
    }

    await this.repository.createAuditEvent({
      tokenId: updated.id,
      tokenSetId: updated.tokenSetId,
      eventType: 'token.updated',
      eventPayload: this.diffObjects(existing, updated),
      createdBy: input.createdBy ?? 'system'
    });

    return updated;
  }

  async deleteToken(id: string, actor = 'system'): Promise<void> {
    const token = await this.getToken(id);
    const deleted = await this.repository.deleteToken(id);
    if (!deleted) {
      throw new DomainError('TOKEN_NOT_FOUND', `Token '${id}' was not found`, 404);
    }

    await this.repository.createAuditEvent({
      tokenSetId: token.tokenSetId,
      eventType: 'token.deleted',
      eventPayload: { id: token.id, name: token.name },
      createdBy: actor
    });
  }

  async approveToken(
    id: string,
    actor = 'system',
    changeNote = 'Token approved'
  ): Promise<DesignToken> {
    const token = await this.getToken(id);
    if (token.status === 'approved') {
      return token;
    }

    return this.updateToken(id, {
      status: 'approved',
      deprecated: false,
      createdBy: actor,
      changeNote
    });
  }

  async rejectToken(
    id: string,
    actor = 'system',
    changeNote = 'Token rejected'
  ): Promise<DesignToken> {
    const token = await this.getToken(id);
    if (token.status === 'deprecated') {
      throw new DomainError('TOKEN_DEPRECATED', 'Deprecated tokens cannot be rejected', 409);
    }

    return this.updateToken(id, {
      status: 'rejected',
      createdBy: actor,
      changeNote
    });
  }

  async deprecateToken(
    id: string,
    actor = 'system',
    changeNote = 'Token deprecated'
  ): Promise<DesignToken> {
    return this.updateToken(id, {
      status: 'deprecated',
      deprecated: true,
      createdBy: actor,
      changeNote
    });
  }

  async listVersions(tokenId: string): Promise<TokenVersion[]> {
    await this.getToken(tokenId);
    return this.repository.listVersions(tokenId);
  }

  async listAuditEvents(filters?: {
    tokenId?: string;
    tokenSetId?: string;
  }): Promise<TokenAuditEvent[]> {
    return this.repository.listAuditEvents(filters);
  }

  async diffTokenSets(fromSetId: string, toSetId: string): Promise<TokenDiffEntry[]> {
    await this.getTokenSet(fromSetId);
    await this.getTokenSet(toSetId);

    const fromTokens = await this.repository.listTokens({ tokenSetId: fromSetId });
    const toTokens = await this.repository.listTokens({ tokenSetId: toSetId });
    const fromByName = new Map(fromTokens.map((token) => [token.name, token]));
    const toByName = new Map(toTokens.map((token) => [token.name, token]));
    const names = [...new Set([...fromByName.keys(), ...toByName.keys()])].sort();

    return names.map((name) => {
      const fromToken = fromByName.get(name);
      const toToken = toByName.get(name);

      if (!fromToken && toToken) {
        return {
          name,
          category: toToken.category,
          toValue: toToken.value,
          changeType: 'added'
        };
      }

      if (fromToken && !toToken) {
        return {
          name,
          category: fromToken.category,
          fromValue: fromToken.value,
          changeType: 'removed'
        };
      }

      if (!fromToken || !toToken) {
        throw new DomainError('TOKEN_DIFF_ERROR', `Unable to compare token '${name}'`, 500);
      }

      return {
        name,
        category: toToken.category,
        fromValue: fromToken.value,
        toValue: toToken.value,
        changeType:
          fromToken.value !== toToken.value || fromToken.category !== toToken.category
            ? 'changed'
            : 'unchanged'
      };
    });
  }

  async listChangelog(tokenSetId?: string): Promise<TokenChangelogEntry[]> {
    if (tokenSetId) {
      await this.getTokenSet(tokenSetId);
    }
    return this.repository.listChangelog(tokenSetId);
  }

  async getStatusSummary(): Promise<{
    title: string;
    description: string;
    status: 'success' | 'warning' | 'danger';
    counts: Record<TokenStatus, number>;
  }> {
    const tokens = await this.repository.listTokens();
    const counts: Record<TokenStatus, number> = {
      draft: 0,
      approved: 0,
      rejected: 0,
      deprecated: 0
    };

    for (const token of tokens) {
      counts[token.status] += 1;
    }

    const status = counts.rejected > 0 ? 'danger' : counts.draft > 0 ? 'warning' : 'success';
    return {
      title: status === 'success' ? 'Design system ready' : 'Design system review needed',
      description: `${counts.approved} approved, ${counts.draft} draft, ${counts.rejected} rejected, ${counts.deprecated} deprecated tokens.`,
      status,
      counts
    };
  }

  private async recordVersion(
    token: DesignToken,
    changeNote: string | undefined,
    createdBy: string | undefined
  ): Promise<TokenVersion> {
    const version = await this.repository.nextVersionNumber(token.id);
    return this.repository.createVersion({
      tokenId: token.id,
      version,
      value: token.value,
      status: token.status,
      changeNote: changeNote ?? null,
      createdBy: createdBy ?? 'system'
    });
  }

  private validateTokenSetName(name: string): void {
    if (!tokenSetNamePattern.test(name)) {
      throw new DomainError(
        'INVALID_TOKEN_SET_NAME',
        'Token set names must be lowercase kebab-case and at least two characters long',
        400
      );
    }
  }

  private validateTokenName(name: string): void {
    if (!tokenNamePattern.test(name)) {
      throw new DomainError(
        'INVALID_TOKEN_NAME',
        'Token names must use lowercase dot notation, for example color.brand-primary or color.brand.primary',
        400
      );
    }
  }

  private validateTokenValue(value: string): void {
    if (value.trim().length === 0) {
      throw new DomainError('INVALID_TOKEN_VALUE', 'Token value cannot be empty', 400);
    }
  }

  private diffObjects(before: DesignToken, after: DesignToken): Record<string, unknown> {
    const diff: Record<string, unknown> = {};
    for (const key of Object.keys(after) as Array<keyof DesignToken>) {
      if (before[key] !== after[key]) {
        diff[key] = {
          before: before[key],
          after: after[key]
        };
      }
    }
    return diff;
  }
}
