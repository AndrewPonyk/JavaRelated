const availabilityService = require("./_lib/availabilityService");
const { badRequest } = require("./_lib/errors");
const { handleError, jsonResponse, parseJsonBody } = require("./_lib/response");
const { validateAvailabilityPayload } = require("./_lib/validation");

exports.handler = async (event) => {
  try {
    if (!["GET", "POST"].includes(event.httpMethod)) {
      throw badRequest("Only GET and POST are supported.");
    }

    const payload =
      event.httpMethod === "GET" ? event.queryStringParameters || {} : parseJsonBody(event);
    const normalized = {
      ...payload,
      guests: Number(payload.guests),
    };
    const validation = validateAvailabilityPayload(normalized);

    if (!validation.valid) {
      throw badRequest(validation.message);
    }

    return jsonResponse(200, {
      data: await availabilityService.findAvailableRooms(normalized),
    });
  } catch (error) {
    return handleError(error, "Unable to check availability.");
  }
};
