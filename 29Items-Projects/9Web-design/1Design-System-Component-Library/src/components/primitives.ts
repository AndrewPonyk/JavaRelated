export class DesignButtonElement extends HTMLElement {
  connectedCallback(): void {
    this.setAttribute('role', this.getAttribute('role') ?? 'button');
    this.tabIndex = this.tabIndex >= 0 ? this.tabIndex : 0;
    this.addEventListener('keydown', this.onKeyDown);
  }

  disconnectedCallback(): void {
    this.removeEventListener('keydown', this.onKeyDown);
  }

  private readonly onKeyDown = (event: KeyboardEvent): void => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.click();
    }
  };
}

export class DesignBadgeElement extends HTMLElement {
  connectedCallback(): void {
    this.setAttribute('role', this.getAttribute('role') ?? 'status');
  }
}

export class DesignAlertElement extends HTMLElement {
  connectedCallback(): void {
    this.setAttribute('role', this.getAttribute('role') ?? 'status');
  }
}

export class DesignModalElement extends HTMLElement {
  connectedCallback(): void {
    this.setAttribute('role', 'dialog');
    this.setAttribute('aria-modal', 'true');
    this.hidden = !this.hasAttribute('open');
  }

  static get observedAttributes(): string[] {
    return ['open'];
  }

  attributeChangedCallback(): void {
    this.hidden = !this.hasAttribute('open');
  }
}

export class DesignTabsElement extends HTMLElement {
  connectedCallback(): void {
    const tabs = [...this.querySelectorAll<HTMLElement>('[role="tab"]')];
    const panels = [...this.querySelectorAll<HTMLElement>('[role="tabpanel"]')];

    tabs.forEach((tab, index) => {
      tab.tabIndex = tab.getAttribute('aria-selected') === 'true' ? 0 : -1;
      tab.addEventListener('click', () => this.activateTab(index, tabs, panels));
      tab.addEventListener('keydown', (event) => {
        if (event.key === 'ArrowRight') {
          event.preventDefault();
          this.activateTab((index + 1) % tabs.length, tabs, panels);
          tabs[(index + 1) % tabs.length].focus();
        }

        if (event.key === 'ArrowLeft') {
          event.preventDefault();
          const nextIndex = (index - 1 + tabs.length) % tabs.length;
          this.activateTab(nextIndex, tabs, panels);
          tabs[nextIndex].focus();
        }
      });
    });
  }

  private activateTab(index: number, tabs: HTMLElement[], panels: HTMLElement[]): void {
    tabs.forEach((tab, tabIndex) => {
      const selected = tabIndex === index;
      tab.setAttribute('aria-selected', String(selected));
      tab.tabIndex = selected ? 0 : -1;
    });

    panels.forEach((panel, panelIndex) => {
      panel.hidden = panelIndex !== index;
    });
  }
}

export function definePrimitives(): void {
  const definitions: Array<[string, CustomElementConstructor]> = [
    ['ds-button', DesignButtonElement],
    ['ds-badge', DesignBadgeElement],
    ['ds-alert', DesignAlertElement],
    ['ds-modal', DesignModalElement],
    ['ds-tabs', DesignTabsElement]
  ];

  for (const [name, constructor] of definitions) {
    if (!customElements.get(name)) {
      customElements.define(name, constructor);
    }
  }
}
