import { escapeHtml } from "../utils/html.js";

export function initHotelMap(elementId, hotels) {
  const element = document.getElementById(elementId);

  if (!element || !window.L) {
    return;
  }

  const center = hotels[0]?.coordinates || [40.758, -73.9855];
  const map = window.L.map(elementId).setView(center, 12);

  window.L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19,
    attribution: "&copy; OpenStreetMap contributors",
  }).addTo(map);

  hotels.forEach((hotel) => {
    if (!hotel.coordinates) {
      return;
    }

    window.L.marker(hotel.coordinates)
      .addTo(map)
      .bindPopup(`<strong>${escapeHtml(hotel.name || hotel.hotelName)}</strong><br>${escapeHtml(hotel.city)}`);
  });
}
