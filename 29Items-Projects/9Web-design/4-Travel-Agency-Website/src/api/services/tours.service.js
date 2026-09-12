const repository = require("../repositories/tours.repository");
const { notFound, validationFailed } = require("../errors");

async function listTours(filters) {
  return repository.listTours(filters);
}

async function getTour(id) {
  const tour = await repository.getTourById(id);

  if (!tour) {
    throw notFound("Tour");
  }

  return tour;
}

async function createTour(payload) {
  const existing = await repository.getTourBySlug(payload.slug);

  if (existing) {
    throw validationFailed([{ field: "slug", message: "Tour slug must be unique." }]);
  }

  return repository.createTour(payload);
}

async function updateTour(id, payload) {
  if (payload.slug) {
    const existing = await repository.getTourBySlug(payload.slug);

    if (existing && String(existing.id) !== String(id)) {
      throw validationFailed([{ field: "slug", message: "Tour slug must be unique." }]);
    }
  }

  const tour = await repository.updateTour(id, payload);

  if (!tour) {
    throw notFound("Tour");
  }

  return tour;
}

async function deleteTour(id) {
  const deleted = await repository.deleteTour(id);

  if (!deleted) {
    throw notFound("Tour");
  }
}

module.exports = { createTour, deleteTour, getTour, listTours, updateTour };
