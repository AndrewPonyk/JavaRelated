const { requireStaff } = require("./_lib/auth");
const { badRequest } = require("./_lib/errors");
const roomService = require("./_lib/roomService");
const { handleError, jsonResponse, parseJsonBody } = require("./_lib/response");
const { isUuid, validateRoomPayload } = require("./_lib/validation");

exports.handler = async (event) => {
  try {
    const id = event.queryStringParameters?.id;

    if (id && !isUuid(id)) {
      throw badRequest("Valid room id is required.");
    }

    if (event.httpMethod === "GET") {
      return jsonResponse(200, {
        data: id ? await roomService.getRoom(id) : await roomService.listRooms(event.queryStringParameters || {}),
      });
    }

    requireStaff(event, "receptionist");

    if (event.httpMethod === "POST") {
      const payload = parseJsonBody(event);
      const validation = validateRoomPayload(payload);

      if (!validation.valid) {
        throw badRequest(validation.message);
      }

      return jsonResponse(201, { data: await roomService.createRoom(payload) });
    }

    if (event.httpMethod === "PATCH") {
      if (!id) {
        throw badRequest("Room id is required.");
      }

      const payload = parseJsonBody(event);
      const validation = validateRoomPayload(payload, true);

      if (!validation.valid) {
        throw badRequest(validation.message);
      }

      return jsonResponse(200, { data: await roomService.updateRoom(id, payload) });
    }

    if (event.httpMethod === "DELETE") {
      requireStaff(event, "manager");

      if (!id) {
        throw badRequest("Room id is required.");
      }

      return jsonResponse(200, { data: await roomService.deleteRoom(id) });
    }

    throw badRequest("Unsupported room method.");
  } catch (error) {
    return handleError(error, "Unable to process room request.");
  }
};
