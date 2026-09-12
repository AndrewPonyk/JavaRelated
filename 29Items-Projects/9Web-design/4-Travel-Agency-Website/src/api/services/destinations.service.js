const repository = require("../repositories/destinations.repository");
const { notFound, validationFailed } = require("../errors");

async function listDestinations(filters) {
  return repository.listDestinations(filters);
}

async function getDestination(id) {
  const destination = await repository.getDestinationById(id);

  if (!destination) {
    throw notFound("Destination");
  }

  return destination;
}

async function createDestination(payload) {
  const existing = await repository.getDestinationBySlug(payload.slug);

  if (existing) {
    throw validationFailed([{ field: "slug", message: "Destination slug must be unique." }]);
  }

  return repository.createDestination(payload);
}

async function updateDestination(id, payload) {
  if (payload.slug) {
    const existing = await repository.getDestinationBySlug(payload.slug);

    if (existing && String(existing.id) !== String(id)) {
      throw validationFailed([{ field: "slug", message: "Destination slug must be unique." }]);
    }
  }

  const destination = await repository.updateDestination(id, payload);

  if (!destination) {
    throw notFound("Destination");
  }

  return destination;
}

async function deleteDestination(id) {
  const deleted = await repository.deleteDestination(id);

  if (!deleted) {
    throw notFound("Destination");
  }
}

module.exports = {
  createDestination,
  deleteDestination,
  getDestination,
  listDestinations,
  updateDestination
};
