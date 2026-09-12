import { reportClientError } from '../clientErrors.js';
import { escapeHtml } from '../html.js';

export class MenuPreview {
  constructor({ mount, filters, source, onFilterChange = () => undefined }) {
    this.mount = mount;
    this.filters = filters;
    this.source = source;
    this.onFilterChange = onFilterChange;
    this.items = [];
    this.activeCategory = 'all';
  }

  async init() {
    if (!this.mount) return;

    this.renderLoading();

    try {
      const response = await fetch(this.source, { headers: { Accept: 'application/json' } });

      if (!response.ok) {
        throw new Error(`Menu request failed with status ${response.status}`);
      }

      const data = await response.json();
      this.items = Array.isArray(data.items) ? data.items : [];
      this.renderFilters();
      this.renderItems();
    } catch (error) {
      this.renderError(error);
    }
  }

  renderLoading() {
    this.mount.innerHTML = '<p class="rounded-md bg-white p-4 text-stone-600">Loading menu...</p>';
  }

  renderError(error) {
    reportClientError('menu_preview', error);
    this.mount.innerHTML =
      '<p class="rounded-md border border-red-200 bg-red-50 p-4 text-red-700">Menu is unavailable right now. Please try again later.</p>';
  }

  renderFilters() {
    if (!this.filters) return;

    const categories = ['all', ...new Set(this.items.map((item) => String(item.category || '').trim()).filter(Boolean))];

    this.filters.innerHTML = categories
      .map(
        (category) => `
          <button
            class="rounded-md border border-stone-300 px-3 py-2 text-sm font-semibold capitalize hover:bg-stone-900 hover:text-white data-[active=true]:bg-stone-950 data-[active=true]:text-white"
            data-menu-category="${escapeHtml(category)}"
            data-active="${category === this.activeCategory}"
            type="button"
          >
            ${escapeHtml(category)}
          </button>
        `
      )
      .join('');

    this.filters.querySelectorAll('[data-menu-category]').forEach((button) => {
      button.addEventListener('click', () => {
        this.activeCategory = button.dataset.menuCategory;
        this.onFilterChange(this.activeCategory);
        this.renderFilters();
        this.renderItems();
      });
    });
  }

  renderItems() {
    const visibleItems =
      this.activeCategory === 'all'
        ? this.items
        : this.items.filter((item) => item.category === this.activeCategory);

    if (visibleItems.length === 0) {
      this.mount.innerHTML = '<p class="rounded-md bg-white p-4 text-stone-600">No dishes found for this category.</p>';
      return;
    }

    this.mount.innerHTML = `
      <div class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        ${visibleItems
          .map(
            (item) => `
              <article class="rounded-lg border border-stone-200 bg-white p-5 shadow-sm">
                <div class="flex items-start justify-between gap-4">
                  <h3 class="text-lg font-bold">${escapeHtml(item.name)}</h3>
                  <p class="font-semibold text-amber-700">${escapeHtml(item.price)}</p>
                </div>
                <p class="mt-3 text-sm leading-6 text-stone-600">${escapeHtml(item.description)}</p>
                <p class="mt-4 text-xs font-semibold uppercase tracking-wide text-stone-400">${escapeHtml(item.category)}</p>
              </article>
            `
          )
          .join('')}
      </div>
    `;
  }
}
