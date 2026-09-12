(function attachFilters(globalScope) {
  function filterTours(tours, criteria) {
    const filters = criteria || {};

    return tours.filter((tour) => {
      const matchesRegion = !filters.region || tour.region === filters.region;
      const matchesPrice = !filters.maxPrice || tour.price <= Number(filters.maxPrice);
      const matchesDuration = !filters.maxDuration || tour.durationDays <= Number(filters.maxDuration);
      const matchesDifficulty = !filters.difficulty || tour.difficulty === filters.difficulty;

      return matchesRegion && matchesPrice && matchesDuration && matchesDifficulty;
    });
  }

  function sortTours(tours, sortKey) {
    const copy = [...tours];

    switch (sortKey) {
      case "price-asc":
        return copy.sort((a, b) => a.price - b.price);
      case "price-desc":
        return copy.sort((a, b) => b.price - a.price);
      case "duration-asc":
        return copy.sort((a, b) => a.durationDays - b.durationDays);
      case "duration-desc":
        return copy.sort((a, b) => b.durationDays - a.durationDays);
      default:
        return copy.sort((a, b) => Number(Boolean(b.featured)) - Number(Boolean(a.featured)));
    }
  }

  const api = { filterTours, sortTours };

  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  }

  globalScope.TravelFilters = api;
})(typeof window !== "undefined" ? window : globalThis);
