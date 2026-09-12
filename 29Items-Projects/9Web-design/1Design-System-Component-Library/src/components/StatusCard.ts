import { fetchJson } from '../utils/fetch-json';

export interface StatusCardData {
  title: string;
  description: string;
  status: 'success' | 'warning' | 'danger';
}

const statusLabels: Record<StatusCardData['status'], string> = {
  success: 'Healthy',
  warning: 'Needs review',
  danger: 'Blocked'
};

const statusColors: Record<StatusCardData['status'], string> = {
  success: 'var(--ds-color-status-success)',
  warning: 'var(--ds-color-status-warning)',
  danger: 'var(--ds-color-status-danger)'
};

export class StatusCardElement extends HTMLElement {
  static observedAttributes = ['data-src', 'heading', 'description', 'status'];

  private abortController?: AbortController;
  private state: 'idle' | 'loading' | 'ready' | 'error' = 'idle';
  private data?: StatusCardData;
  private errorMessage = '';

  connectedCallback(): void {
    void this.load();
  }

  disconnectedCallback(): void {
    this.abortController?.abort();
  }

  attributeChangedCallback(): void {
    if (this.isConnected) {
      void this.load();
    }
  }

  private async load(): Promise<void> {
    const src = this.getAttribute('data-src');

    if (!src) {
      this.data = {
        title: this.getAttribute('heading') ?? 'Design token status',
        description: this.getAttribute('description') ?? 'Token source is ready for review.',
        status: this.normalizeStatus(this.getAttribute('status'))
      };
      this.state = 'ready';
      this.render();
      return;
    }

    this.abortController?.abort();
    this.abortController = new AbortController();
    this.state = 'loading';
    this.render();

    try {
      this.data = await fetchJson<StatusCardData>(src, {
        signal: this.abortController.signal
      });
      this.state = 'ready';
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        return;
      }

      this.errorMessage = error instanceof Error ? error.message : 'Unknown loading error';
      this.state = 'error';
    }

    this.render();
  }

  private normalizeStatus(status: string | null): StatusCardData['status'] {
    if (status === 'warning' || status === 'danger') {
      return status;
    }

    return 'success';
  }

  private render(): void {
    if (this.state === 'loading') {
      this.innerHTML = this.wrap(`
        <div class="ds-status-card__meta" role="status" aria-live="polite">Loading component status...</div>
      `);
      return;
    }

    if (this.state === 'error') {
      this.innerHTML = this.wrap(`
        <p class="ds-status-card__eyebrow">Status unavailable</p>
        <h2 class="ds-status-card__title">Unable to load status</h2>
        <p class="ds-status-card__description">${this.escapeHtml(this.errorMessage)}</p>
      `);
      return;
    }

    const data = this.data;
    if (!data) {
      return;
    }

    this.innerHTML = this.wrap(`
      <p class="ds-status-card__eyebrow" style="--status-color: ${statusColors[data.status]}">
        ${statusLabels[data.status]}
      </p>
      <h2 class="ds-status-card__title">${this.escapeHtml(data.title)}</h2>
      <p class="ds-status-card__description">${this.escapeHtml(data.description)}</p>
    `);
  }

  private wrap(content: string): string {
    return `
      <article class="ds-status-card" aria-label="Design system status">
        ${content}
      </article>
    `;
  }

  private escapeHtml(value: string): string {
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

export function defineStatusCard(): void {
  if (!customElements.get('ds-status-card')) {
    customElements.define('ds-status-card', StatusCardElement);
  }
}
