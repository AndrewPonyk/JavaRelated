const service = require("../services/inquiries.service");
const { validateInquiryPayload } = require("../validators/inquiry.validator");
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

async function handleInquiryRequest(event) {
  try {
    const id = parsePathId(event, "inquiries");

    switch (event.httpMethod) {
      case "GET":
        return id ? json(200, { data: await service.getInquiry(id) }) : list(event);
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
  const result = await service.listInquiries({ ...query, ...pagination });

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
  const payload = parseJsonBody(event.body);
  const result = validateInquiryPayload(payload);

  if (!result.valid) {
    return validationError(result.errors);
  }

  return json(201, { data: await service.createInquiry(result.value) });
}

async function update(event, id) {
  const payload = parseJsonBody(event.body);
  const targetId = id || payload.id;
  const result = validateInquiryPayload(payload, { partial: true });

  if (!targetId) {
    return validationError([{ field: "id", message: "Inquiry id is required." }]);
  }

  if (!result.valid) {
    return validationError(result.errors);
  }

  return json(200, { data: await service.updateInquiry(targetId, result.value) });
}

async function remove(event, id) {
  const body = parseJsonBody(event.body);
  const targetId = id || body.id;

  if (!targetId) {
    return validationError([{ field: "id", message: "Inquiry id is required." }]);
  }

  await service.deleteInquiry(targetId);
  return json(204, null);
}

module.exports = { handleInquiryRequest };
