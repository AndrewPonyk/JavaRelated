const { requireStaff } = require("./_lib/auth");
const { badRequest } = require("./_lib/errors");
const hotelService = require("./_lib/hotelService");
const { handleError, jsonResponse, parseJsonBody } = require("./_lib/response");
const { isUuid, validateHotelPayload } = require("./_lib/validation");

exports.handler = async (event) => {
  try {
    const id = event.queryStringParameters?.id;

    if (id && !isUuid(id)) {
      throw badRequest("Valid hotel id is required.");
    }

    if (event.httpMethod === "GET") {
      return jsonResponse(200, {
        data: id
          ? await hotelService.getHotel(id)
          : await hotelService.listHotels(event.queryStringParameters || {}),
      });
    }

    requireStaff(event, "manager");

    if (event.httpMethod === "POST") {
      const payload = parseJsonBody(event);
      const validation = validateHotelPayload(payload);

      if (!validation.valid) {
        throw badRequest(validation.message);
      }

      return jsonResponse(201, { data: await hotelService.createHotel(payload) });
    }

    if (event.httpMethod === "PATCH") {
      if (!id) {
        throw badRequest("Hotel id is required.");
      }

      const payload = parseJsonBody(event);
      const validation = validateHotelPayload(payload, true);

      if (!validation.valid) {
        throw badRequest(validation.message);
      }

      return jsonResponse(200, { data: await hotelService.updateHotel(id, payload) });
    }

    if (event.httpMethod === "DELETE") {
      if (!id) {
        throw badRequest("Hotel id is required.");
      }

      return jsonResponse(200, { data: await hotelService.deleteHotel(id) });
    }

    throw badRequest("Unsupported hotel method.");
  } catch (error) {
    return handleError(error, "Unable to process hotel request.");
  }
};
