(function bootstrapSite() {
  function initAos() {
    if (window.AOS) {
      window.AOS.init({ duration: 650, once: true, offset: 80 });
    }
  }

  async function initFeaturedTours() {
    const target = document.querySelector("#featuredTours");

    if (!target) {
      return;
    }

    try {
      const response = await fetch("/api/tours?featured=true");
      const body = await response.json();
      const tours = (Array.isArray(body) ? body : body.data).slice(0, 6);

      target.innerHTML = tours
        .map((tour) => {
          const title = escapeHtml(tour.title);
          const summary = escapeHtml(tour.summary);
          const image = escapeHtml(tour.image);
          const slug = encodeURIComponent(tour.slug || "");

          return `
            <div class="swiper-slide">
              <div class="tour-card">
                <img src="${image}" alt="${title}" loading="lazy">
                <div class="tour-card-body">
                  <h2 class="h5">${title}</h2>
                  <p class="text-secondary">${summary}</p>
                  <a class="btn btn-sm btn-outline-primary" href="/src/pages/contact.html?tour=${slug}">Inquire</a>
                </div>
              </div>
            </div>
          `;
        })
        .join("");

      if (window.Swiper) {
        new window.Swiper(".tour-swiper", {
          slidesPerView: 1,
          spaceBetween: 20,
          pagination: { el: ".swiper-pagination", clickable: true },
          breakpoints: {
            768: { slidesPerView: 2 },
            1200: { slidesPerView: 3 }
          }
        });
      }
    } catch (error) {
      console.error(error);
      target.innerHTML = '<div class="swiper-slide"><div class="error-state text-danger">Featured tours could not be loaded.</div></div>';
    }
  }

  function initTourList() {
    const container = document.querySelector("[data-tour-list]");

    if (!container || !window.TourList) {
      return;
    }

    const tourList = new window.TourList(container, { source: container.dataset.source });
      const controls = {
        region: document.querySelector("#regionFilter"),
        difficulty: document.querySelector("#difficultyFilter"),
        maxPrice: document.querySelector("#maxPriceFilter"),
        maxDuration: document.querySelector("#maxDurationFilter"),
        sort: document.querySelector("#sortTours")
    };

    function readCriteria() {
      return {
        region: controls.region.value,
        difficulty: controls.difficulty.value,
        maxPrice: controls.maxPrice.value,
        maxDuration: controls.maxDuration.value
      };
    }

    Object.values(controls).forEach((control) => {
      if (control) {
        control.addEventListener("change", () => tourList.update(readCriteria(), controls.sort.value));
      }
    });

    tourList.init();
  }

  function initInquiryForm() {
    const form = document.querySelector("#inquiryForm");
    const status = document.querySelector("#formStatus");

    if (!form || !status) {
      return;
    }

    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      status.className = "form-status text-secondary";
      status.textContent = "Sending inquiry...";

      if (!form.checkValidity()) {
        form.classList.add("was-validated");
        status.className = "form-status text-danger";
        status.textContent = "Please complete the required fields.";
        return;
      }

      const payload = Object.fromEntries(new FormData(form).entries());
      payload.consent = form.elements.consent.checked;

      try {
        const response = await fetch("/api/inquiries", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload)
        });
        const body = await response.json();

        if (!response.ok) {
          throw new Error(body.error?.message || "Inquiry could not be sent.");
        }

        form.reset();
        status.className = "form-status text-success";
        status.textContent = "Inquiry received. We will respond with next steps.";
      } catch (error) {
        status.className = "form-status text-danger";
        status.textContent = error.message;
      }
    });
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

  document.addEventListener("DOMContentLoaded", () => {
    initAos();
    initFeaturedTours();
    initTourList();
    initInquiryForm();

    if (window.initDestinationMap) {
      window.initDestinationMap();
    }
  });
})();
