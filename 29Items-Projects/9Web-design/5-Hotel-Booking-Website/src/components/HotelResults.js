import { escapeHtml } from "../utils/html.js";

function formatCurrency(value) {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 0,
  }).format(Number(value || 0));
}

function createHotelCard(hotel) {
  const amenities = escapeHtml(hotel.amenities?.join(", ") || "Amenities pending");
  const total = hotel.totalPrice ? `Total ${formatCurrency(hotel.totalPrice)}` : "";
  const name = escapeHtml(hotel.name || hotel.hotelName);
  const imageUrl = escapeHtml(hotel.imageUrl || "");
  const roomType = escapeHtml(hotel.roomType || "");
  const city = escapeHtml(hotel.city || "");
  const roomId = escapeHtml(hotel.id || "");
  const capacity = Number.isFinite(Number(hotel.capacity)) ? Number(hotel.capacity) : 0;

  return `
    <article class="col-md-6 col-xl-4" data-aos="fade-up">
      <div class="hotel-card">
        <img src="${imageUrl}" alt="${name}" loading="lazy" />
        <div class="p-3">
          <div class="d-flex justify-content-between align-items-start gap-3">
            <h3 class="h5 mb-1">${name}</h3>
            <span class="badge text-bg-light">${roomType}</span>
          </div>
          <p class="text-secondary small mb-2">${city}</p>
          <p class="mb-3">${amenities}</p>
          <div class="d-flex justify-content-between align-items-center">
            <span class="fw-semibold">${formatCurrency(hotel.pricePerNight)} / night</span>
            <span class="small text-secondary">Up to ${capacity} guests</span>
          </div>
          <div class="d-flex justify-content-between align-items-center mt-3">
            <span class="small text-secondary">${total}</span>
            <button
              class="btn btn-sm btn-outline-primary"
              type="button"
              data-room-id="${roomId}"
            >
              Select
            </button>
          </div>
        </div>
      </div>
    </article>
  `;
}

export function renderHotelResults(container, hotels, error = null) {
  if (!container) {
    return;
  }

  if (error) {
    container.innerHTML = `
      <div class="col-12">
        <div class="alert alert-danger mb-0">${escapeHtml(error.message)}</div>
      </div>
    `;
    return;
  }

  if (!hotels.length) {
    container.innerHTML = `
      <div class="col-12">
        <div class="alert alert-info mb-0">No rooms match the current search.</div>
      </div>
    `;
    return;
  }

  container.innerHTML = hotels.map(createHotelCard).join("");
}
