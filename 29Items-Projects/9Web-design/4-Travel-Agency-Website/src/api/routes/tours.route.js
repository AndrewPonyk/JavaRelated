const service = require("../services/tours.service");
const { validateTourPayload } = require("../validators/tour.validator");
const {
  errorResponse,
  json,
  methodNotAllowed,
  parseJsonBody,
  parsePagination,
  parsePathId,
  parseQuery,
  validationError
} = require("../utils/http");

async function handleTourRequest(event) {
  try {
    const id = parsePathId(event, "tours");

    switch (event.httpMethod) {
      case "GET":
        return id ? json(200, { data: await service.getTour(id) }) : list(event);
      case "POST":
        return create(event);
      case "PATCH":
        return update(event, id);
      case "DELETE":
        return remove(event, id);
      default:
        return methodNotAllowed(["GET", "POST", "PATCH", "DELETE"]);
    }
  } catch (error) {
    return errorResponse(error);
  }
}

async function list(event) {
  const query = normalizeQuery(parseQuery(event));
  const pagination = parsePagination(query);
  const result = await service.listTours({ ...query, ...pagination });

  return json(200, {
    data: result.items,
    meta: {
      total: result.total,
      count: result.items.length,
      limit: pagination.limit,
      offset: pagination.offset
    }
  });
}

async function create(event) {
  const result = validateTourPayload(parseJsonBody(event.body));

  if (!result.valid) {
    return validationError(result.errors);
  }

  return json(201, { data: await service.createTour(result.value) });
}

async function update(event, id) {
  const payload = parseJsonBody(event.body);
  const targetId = id || payload.id;
  const result = validateTourPayload(payload, { partial: true });

  if (!targetId) {
    return validationError([{ field: "id", message: "Tour id is required." }]);
  }

  if (!result.valid) {
    return validationError(result.errors);
  }

  return json(200, { data: await service.updateTour(targetId, result.value) });
}

async function remove(event, id) {
  const payload = parseJsonBody(event.body);
  const targetId = id || payload.id;

  if (!targetId) {
    return validationError([{ field: "id", message: "Tour id is required." }]);
  }

  await service.deleteTour(targetId);
  return json(204, null);
}

function normalizeQuery(query) {
  return {
    ...query,
    featured: query.featured === undefined ? undefined : query.featured === "true"
  };
}

module.exports = { handleTourRequest };
