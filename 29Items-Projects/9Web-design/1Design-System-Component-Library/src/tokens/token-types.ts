import type { DesignToken as DomainDesignToken } from '../services/token-models';

export type {
  CreateTokenInput,
  CreateTokenSetInput,
  DesignToken,
  TokenAuditEvent,
  TokenCategory,
  TokenChangelogEntry,
  TokenDiffEntry,
  TokenFilters,
  TokenSet,
  TokenStatus,
  TokenVersion,
  UpdateTokenInput,
  UpdateTokenSetInput
} from '../services/token-models';

export interface DesignTokenGroup {
  version: string;
  tokens: DomainDesignToken[];
}
