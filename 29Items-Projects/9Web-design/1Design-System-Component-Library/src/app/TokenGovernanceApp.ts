import type { DesignToken, TokenCategory, TokenSet } from '../services/token-models';
import { fetchJson } from '../utils/fetch-json';

type ApiEnvelope<T> = { data: T };

const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:4100';

const categories: TokenCategory[] = [
  'color',
  'typography',
  'spacing',
  'radius',
  'shadow',
  'motion',
  'zIndex'
];
export class TokenGovernanceAppElement extends HTMLElement {
  private tokenSets: TokenSet[] = [];
  private tokens: DesignToken[] = [];
  private selectedTokenSetId = '';
  private selectedTokenId = '';
  private loading = true;
  private saving = false;
  private error = '';
  private success = '';

  connectedCallback(): void {
    void this.load();
  }

  private async load(): Promise<void> {
    this.loading = true;
    this.error = '';
    this.render();

    try {
      const tokenSets = await fetchJson<ApiEnvelope<TokenSet[]>>(`${apiBase}/api/token-sets`);
      this.tokenSets = tokenSets.data;
      this.selectedTokenSetId = this.selectedTokenSetId || this.tokenSets[0]?.id || '';
      await this.loadTokens();
    } catch (error) {
      this.error = this.toMessage(error);
    } finally {
      this.loading = false;
      this.render();
    }
  }

  private async loadTokens(): Promise<void> {
    if (!this.selectedTokenSetId) {
      this.tokens = [];
      return;
    }

    const tokens = await fetchJson<ApiEnvelope<DesignToken[]>>(
      `${apiBase}/api/tokens?tokenSetId=${encodeURIComponent(this.selectedTokenSetId)}`
    );
    this.tokens = tokens.data;
    this.selectedTokenId = this.tokens[0]?.id || '';
  }

  private async submitToken(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    const form = event.currentTarget as HTMLFormElement;
    const formData = new FormData(form);
    const payload = {
      tokenSetId: this.selectedTokenSetId,
      name: String(formData.get('name') ?? ''),
      category: String(formData.get('category') ?? 'color') as TokenCategory,
      value: String(formData.get('value') ?? ''),
      description: String(formData.get('description') ?? ''),
      createdBy: 'ui',
      changeNote: 'Created from governance UI'
    };

    if (!payload.tokenSetId || !payload.name || !payload.value) {
      this.error = 'Token set, name, and value are required.';
      this.render();
      return;
    }

    await this.mutate(async () => {
      await fetchJson<ApiEnvelope<DesignToken>>(`${apiBase}/api/tokens`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(payload)
      });
      form.reset();
      this.success = 'Token created.';
      await this.loadTokens();
    });
  }

  private async updateToken(
    id: string,
    patch: Partial<DesignToken> & { changeNote?: string }
  ): Promise<void> {
    await this.mutate(async () => {
      await fetchJson<ApiEnvelope<DesignToken>>(`${apiBase}/api/tokens/${id}`, {
        method: 'PATCH',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ ...patch, createdBy: 'ui' })
      });
      this.success = 'Token updated.';
      await this.loadTokens();
    });
  }

  private async lifecycle(id: string, action: 'approve' | 'reject' | 'deprecate'): Promise<void> {
    await this.mutate(async () => {
      await fetchJson<ApiEnvelope<DesignToken>>(`${apiBase}/api/tokens/${id}/${action}`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ actor: 'ui', changeNote: `Token ${action}d from governance UI` })
      });
      this.success = `Token ${action}d.`;
      await this.loadTokens();
    });
  }

  private async removeToken(id: string): Promise<void> {
    await this.mutate(async () => {
      await fetch(`${apiBase}/api/tokens/${id}?actor=ui`, { method: 'DELETE' });
      this.success = 'Token deleted.';
      await this.loadTokens();
    });
  }

  private async mutate(operation: () => Promise<void>): Promise<void> {
    this.saving = true;
    this.error = '';
    this.success = '';
    this.render();

    try {
      await operation();
    } catch (error) {
      this.error = this.toMessage(error);
    } finally {
      this.saving = false;
      this.render();
    }
  }

  private toMessage(error: unknown): string {
    if (error && typeof error === 'object' && 'details' in error) {
      return JSON.stringify((error as { details: unknown }).details);
    }

    return error instanceof Error ? error.message : 'Unexpected error';
  }

  private render(): void {
    if (this.loading) {
      this.innerHTML =
        '<div class="app-shell"><ds-alert>Loading design-system data...</ds-alert></div>';
      return;
    }

    const selected = this.tokens.find((token) => token.id === this.selectedTokenId);

    this.innerHTML = `
      <main class="app-shell">
        <section class="app-header">
          <div>
            <p class="eyebrow">Design System Governance</p>
            <h1>Token library</h1>
          </div>
          <ds-status-card data-src="${apiBase}/api/status"></ds-status-card>
        </section>

        ${this.error ? `<ds-alert class="alert-error">${this.escape(this.error)}</ds-alert>` : ''}
        ${this.success ? `<ds-alert class="alert-success">${this.escape(this.success)}</ds-alert>` : ''}

        <section class="toolbar" aria-label="Token filters">
          <label>
            Token set
            <select id="token-set-select">
              ${this.tokenSets
                .map(
                  (set) =>
                    `<option value="${set.id}" ${set.id === this.selectedTokenSetId ? 'selected' : ''}>${this.escape(
                      set.name
                    )}</option>`
                )
                .join('')}
            </select>
          </label>
          <button type="button" id="refresh-button">Refresh</button>
        </section>

        <section class="workspace">
          <form class="token-form" id="token-form">
            <h2>Create token</h2>
            <label>Name <input name="name" required pattern="[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+" /></label>
            <label>Category
              <select name="category">
                ${categories.map((category) => `<option value="${category}">${category}</option>`).join('')}
              </select>
            </label>
            <label>Value <input name="value" required /></label>
            <label>Description <textarea name="description" rows="3"></textarea></label>
            <button type="submit" ${this.saving ? 'disabled' : ''}>Create token</button>
          </form>

          <div class="token-panel">
            ${this.renderTokenTable()}
            ${selected ? this.renderSelectedToken(selected) : '<p class="empty">Select a token to inspect it.</p>'}
          </div>
        </section>
      </main>
    `;

    this.bindEvents();
  }

  private renderTokenTable(): string {
    if (this.tokens.length === 0) {
      return '<p class="empty">No tokens in this set yet.</p>';
    }

    return `
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Category</th>
              <th>Value</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            ${this.tokens
              .map(
                (token) => `
                  <tr data-token-id="${token.id}" class="${token.id === this.selectedTokenId ? 'selected' : ''}">
                    <td>${this.escape(token.name)}</td>
                    <td>${token.category}</td>
                    <td><code>${this.escape(token.value)}</code></td>
                    <td><ds-badge class="status-${token.status}">${token.status}</ds-badge></td>
                  </tr>
                `
              )
              .join('')}
          </tbody>
        </table>
      </div>
    `;
  }

  private renderSelectedToken(token: DesignToken): string {
    return `
      <aside class="inspector" aria-label="Selected token">
        <h2>${this.escape(token.name)}</h2>
        <label>Value <input id="edit-value" value="${this.escape(token.value)}" /></label>
        <label>Description
          <textarea id="edit-description" rows="3">${this.escape(token.description ?? '')}</textarea>
        </label>
        <div class="actions">
          <button type="button" id="save-token">Save</button>
          <button type="button" id="approve-token">Approve</button>
          <button type="button" id="reject-token">Reject</button>
          <button type="button" id="deprecate-token">Deprecate</button>
          <button type="button" id="delete-token" class="danger">Delete</button>
        </div>
      </aside>
    `;
  }

  private bindEvents(): void {
    this.querySelector('#token-set-select')?.addEventListener('change', async (event) => {
      this.selectedTokenSetId = (event.currentTarget as HTMLSelectElement).value;
      await this.mutate(async () => {
        await this.loadTokens();
      });
    });

    this.querySelector('#refresh-button')?.addEventListener('click', () => {
      void this.load();
    });

    this.querySelector('#token-form')?.addEventListener('submit', (event) => {
      void this.submitToken(event as SubmitEvent);
    });

    this.querySelectorAll('tr[data-token-id]').forEach((row) => {
      row.addEventListener('click', () => {
        this.selectedTokenId = (row as HTMLElement).dataset.tokenId ?? '';
        this.render();
      });
    });

    const selectedId = this.selectedTokenId;
    this.querySelector('#save-token')?.addEventListener('click', () => {
      const value = (this.querySelector('#edit-value') as HTMLInputElement | null)?.value ?? '';
      const description =
        (this.querySelector('#edit-description') as HTMLTextAreaElement | null)?.value ?? '';
      void this.updateToken(selectedId, {
        value,
        description,
        changeNote: 'Updated from governance UI'
      });
    });
    this.querySelector('#approve-token')?.addEventListener(
      'click',
      () => void this.lifecycle(selectedId, 'approve')
    );
    this.querySelector('#reject-token')?.addEventListener(
      'click',
      () => void this.lifecycle(selectedId, 'reject')
    );
    this.querySelector('#deprecate-token')?.addEventListener(
      'click',
      () => void this.lifecycle(selectedId, 'deprecate')
    );
    this.querySelector('#delete-token')?.addEventListener(
      'click',
      () => void this.removeToken(selectedId)
    );
  }

  private escape(value: string): string {
    return value.replace(/[&<>"']/g, (character) => {
      const entities: Record<string, string> = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
      };
      return entities[character];
    });
  }
}

export function defineTokenGovernanceApp(): void {
  if (!customElements.get('token-governance-app')) {
    customElements.define('token-governance-app', TokenGovernanceAppElement);
  }
}
