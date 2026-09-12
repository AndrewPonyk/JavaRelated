const service = require("../services/destinations.service");
const { validateDestinationPayload } = require("../validators/destination.validator");
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

async function handleDestinationRequest(event) {
  try {
    const id = parsePathId(event, "destinations");

    switch (event.httpMethod) {
      case "GET":
        return id
          ? json(200, { data: await service.getDestination(id) })
          : list(event);
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
  const query = parseQuery(event);
  const pagination = parsePagination(query);
  const result = await service.listDestinations({ ...query, ...pagination });

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
  const result = validateDestinationPayload(parseJsonBody(event.body));

  if (!result.valid) {
    return validationError(result.errors);
  }

  return json(201, { data: await service.createDestination(result.value) });
}

async function update(event, id) {
  const payload = parseJsonBody(event.body);
  const targetId = id || payload.id;
  const result = validateDestinationPayload(payload, { partial: true });

  if (!targetId) {
    return validationError([{ field: "id", message: "Destination id is required." }]);
  }

  if (!result.valid) {
    return validationError(result.errors);
  }

  return json(200, { data: await service.updateDestination(targetId, result.value) });
}

async function remove(event, id) {
  const payload = parseJsonBody(event.body);
  const targetId = id || payload.id;

  if (!targetId) {
    return validationError([{ field: "id", message: "Destination id is required." }]);
  }

  await service.deleteDestination(targetId);
  return json(204, null);
}

module.exports = { handleDestinationRequest };
