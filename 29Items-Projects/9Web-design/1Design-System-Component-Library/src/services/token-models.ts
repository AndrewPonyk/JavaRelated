export type TokenCategory =
  | 'color'
  | 'typography'
  | 'spacing'
  | 'radius'
  | 'shadow'
  | 'motion'
  | 'zIndex';

export type TokenStatus = 'draft' | 'approved' | 'rejected' | 'deprecated';

export interface TokenSet {
  id: string;
  name: string;
  description: string | null;
  source: string;
  createdAt: string;
  updatedAt: string;
}

export interface DesignToken<TValue = string> {
  id: string;
  tokenSetId: string;
  name: string;
  category: TokenCategory;
  value: TValue;
  description: string | null;
  status: TokenStatus;
  deprecated: boolean;
  figmaNodeId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TokenVersion {
  id: string;
  tokenId: string;
  version: number;
  value: string;
  status: TokenStatus;
  changeNote: string | null;
  createdBy: string | null;
  createdAt: string;
}

export interface TokenAuditEvent {
  id: string;
  tokenId: string | null;
  tokenSetId: string | null;
  eventType: string;
  eventPayload: Record<string, unknown>;
  createdBy: string | null;
  createdAt: string;
}

export interface CreateTokenSetInput {
  name: string;
  description?: string;
  source?: string;
}

export interface UpdateTokenSetInput {
  name?: string;
  description?: string | null;
  source?: string;
}

export interface CreateTokenInput {
  tokenSetId: string;
  name: string;
  category: TokenCategory;
  value: string;
  description?: string | null;
  figmaNodeId?: string | null;
  createdBy?: string;
  changeNote?: string;
}

export interface UpdateTokenInput {
  name?: string;
  category?: TokenCategory;
  value?: string;
  description?: string | null;
  status?: TokenStatus;
  deprecated?: boolean;
  figmaNodeId?: string | null;
  createdBy?: string;
  changeNote?: string;
}

export interface TokenFilters {
  tokenSetId?: string;
  category?: TokenCategory;
  status?: TokenStatus;
  search?: string;
}

export interface TokenDiffEntry {
  name: string;
  category: TokenCategory;
  fromValue?: string;
  toValue?: string;
  changeType: 'added' | 'removed' | 'changed' | 'unchanged';
}

export interface TokenChangelogEntry {
  tokenName: string;
  version: number;
  value: string;
  status: TokenStatus;
  changeNote: string | null;
  createdBy: string | null;
  createdAt: string;
}
