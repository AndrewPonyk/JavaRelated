const { requireStaff } = require("./_lib/auth");
const { badRequest } = require("./_lib/errors");
const guestService = require("./_lib/guestService");
const { handleError, jsonResponse, parseJsonBody } = require("./_lib/response");
const { isUuid, validateGuestPayload } = require("./_lib/validation");

exports.handler = async (event) => {
  try {
    requireStaff(event, "receptionist");
    const id = event.queryStringParameters?.id;

    if (id && !isUuid(id)) {
      throw badRequest("Valid guest id is required.");
    }

    if (event.httpMethod === "GET") {
      return jsonResponse(200, {
        data: id ? await guestService.getGuest(id) : await guestService.listGuests(event.queryStringParameters || {}),
      });
    }

    if (event.httpMethod === "POST") {
      const payload = parseJsonBody(event);
      const validation = validateGuestPayload(payload);

      if (!validation.valid) {
        throw badRequest(validation.message);
      }

      return jsonResponse(201, { data: await guestService.createGuest(payload) });
    }

    if (event.httpMethod === "PATCH") {
      if (!id) {
        throw badRequest("Guest id is required.");
      }

      const payload = parseJsonBody(event);
      const validation = validateGuestPayload(payload, true);

      if (!validation.valid) {
        throw badRequest(validation.message);
      }

      return jsonResponse(200, { data: await guestService.updateGuest(id, payload) });
    }

    if (event.httpMethod === "DELETE") {
      requireStaff(event, "manager");

      if (!id) {
        throw badRequest("Guest id is required.");
      }

      return jsonResponse(200, { data: await guestService.deleteGuest(id) });
    }

    throw badRequest("Unsupported guest method.");
  } catch (error) {
    return handleError(error, "Unable to process guest request.");
  }
};
