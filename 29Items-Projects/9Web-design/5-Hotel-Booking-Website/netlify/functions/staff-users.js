const { requireStaff } = require("./_lib/auth");
const { badRequest } = require("./_lib/errors");
const { handleError, jsonResponse, parseJsonBody } = require("./_lib/response");
const staffUserService = require("./_lib/staffUserService");
const { isUuid, validateStaffUserPayload } = require("./_lib/validation");

exports.handler = async (event) => {
  try {
    requireStaff(event, "manager");
    const id = event.queryStringParameters?.id;

    if (id && !isUuid(id)) {
      throw badRequest("Valid staff user id is required.");
    }

    if (event.httpMethod === "GET") {
      return jsonResponse(200, {
        data: id
          ? await staffUserService.getStaffUser(id)
          : await staffUserService.listStaffUsers(event.queryStringParameters || {}),
      });
    }

    if (event.httpMethod === "POST") {
      const payload = parseJsonBody(event);
      const validation = validateStaffUserPayload(payload);

      if (!validation.valid) {
        throw badRequest(validation.message);
      }

      return jsonResponse(201, { data: await staffUserService.createStaffUser(payload) });
    }

    if (event.httpMethod === "PATCH") {
      if (!id) {
        throw badRequest("Staff user id is required.");
      }

      const payload = parseJsonBody(event);
      const validation = validateStaffUserPayload(payload, true);

      if (!validation.valid) {
        throw badRequest(validation.message);
      }

      return jsonResponse(200, { data: await staffUserService.updateStaffUser(id, payload) });
    }

    if (event.httpMethod === "DELETE") {
      if (!id) {
        throw badRequest("Staff user id is required.");
      }

      return jsonResponse(200, { data: await staffUserService.deleteStaffUser(id) });
    }

    throw badRequest("Unsupported staff user method.");
  } catch (error) {
    return handleError(error, "Unable to process staff user request.");
  }
};
