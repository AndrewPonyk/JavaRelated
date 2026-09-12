(function attachDestinationMap(globalScope) {
  async function initDestinationMap() {
    const mapElement = document.querySelector("#destinationMap");

    if (!mapElement || !globalScope.L) {
      return;
    }

    const source = mapElement.dataset.source || "/src/assets/data/destinations.json";
    const map = globalScope.L.map(mapElement).setView([32, 12], 2);

    globalScope.L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      maxZoom: 18
    }).addTo(map);

    try {
      const response = await fetch(source);

      if (!response.ok) {
        throw new Error(`Destination request failed with status ${response.status}`);
      }

      const body = await response.json();
      const destinations = Array.isArray(body) ? body : body.data;
      destinations.forEach((destination) => {
        globalScope.L.marker([destination.latitude, destination.longitude])
          .addTo(map)
          .bindPopup(`<strong>${escapeHtml(destination.name)}</strong><br>${escapeHtml(destination.description)}`);
      });
    } catch (error) {
      console.error(error);
      mapElement.insertAdjacentHTML("beforeend", '<div class="map-error">Destination markers could not be loaded.</div>');
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

  globalScope.initDestinationMap = initDestinationMap;
})(window);
