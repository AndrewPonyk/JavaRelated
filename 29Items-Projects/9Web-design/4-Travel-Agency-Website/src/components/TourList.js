(function attachTourList(globalScope) {
  class TourList {
    constructor(container, options) {
      this.container = container;
      this.source = options.source;
      this.filters = options.filters || {};
      this.sort = options.sort || "featured";
      this.tours = [];
    }

    async init() {
      this.renderLoading();

      try {
        const response = await fetch(this.source);

        if (!response.ok) {
          throw new Error(`Tour request failed with status ${response.status}`);
        }

        const body = await response.json();
        this.tours = Array.isArray(body) ? body : body.data;
        this.render();
      } catch (error) {
        this.renderError(error);
      }
    }

    update(criteria, sortKey) {
      this.filters = criteria;
      this.sort = sortKey;
      this.render();
    }

    getVisibleTours() {
      const filterApi = globalScope.TravelFilters;
      const filtered = filterApi.filterTours(this.tours, this.filters);
      return filterApi.sortTours(filtered, this.sort);
    }

    renderLoading() {
      this.container.innerHTML = '<div class="col-12"><div class="loading-shell">Loading tours...</div></div>';
    }

    renderError(error) {
      console.error(error);
      this.container.innerHTML = '<div class="col-12"><div class="error-state text-danger">Tours could not be loaded. Please try again later.</div></div>';
    }

    render() {
      const tours = this.getVisibleTours();

      if (tours.length === 0) {
        this.container.innerHTML = '<div class="col-12"><div class="empty-state">No tours match the selected filters.</div></div>';
        return;
      }

      this.container.innerHTML = tours.map((tour) => this.renderTourCard(tour)).join("");
    }

    renderTourCard(tour) {
      const title = escapeHtml(tour.title);
      const regionLabel = escapeHtml(tour.regionLabel);
      const difficulty = escapeHtml(tour.difficulty);
      const summary = escapeHtml(tour.summary);
      const image = escapeHtml(tour.image);
      const slug = encodeURIComponent(tour.slug || "");

      return `
        <article class="col-md-6 col-xl-4" data-aos="fade-up">
          <div class="tour-card">
            <img src="${image}" alt="${title}" loading="lazy">
            <div class="tour-card-body">
              <div class="tour-meta mb-2">
                <span><i class="bi bi-geo-alt"></i> ${regionLabel}</span>
                <span><i class="bi bi-calendar3"></i> ${tour.durationDays} days</span>
                <span><i class="bi bi-signpost"></i> ${difficulty}</span>
              </div>
              <h2 class="h5">${title}</h2>
              <p class="text-secondary">${summary}</p>
              <div class="d-flex justify-content-between align-items-center">
                <strong>$${tour.price.toLocaleString()}</strong>
                <a class="btn btn-sm btn-outline-primary" href="/src/pages/contact.html?tour=${slug}">Inquire</a>
              </div>
            </div>
          </div>
        </article>
      `;
    }
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

  globalScope.TourList = TourList;
})(window);
