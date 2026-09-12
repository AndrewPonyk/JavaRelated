const { requireStaff } = require("./_lib/auth");
const bookingService = require("./_lib/bookingService");
const { handleError, jsonResponse, parseJsonBody } = require("./_lib/response");
const { isUuid, validateBookingPayload, validateStatusPayload } = require("./_lib/validation");
const { badRequest } = require("./_lib/errors");

exports.handler = async (event) => {
  try {
    const id = event.queryStringParameters?.id;
    const confirmationCode = event.queryStringParameters?.confirmationCode;

    if (id && !isUuid(id)) {
      throw badRequest("Valid booking id is required.");
    }

    if (event.httpMethod === "GET") {
      if (id) {
        requireStaff(event, "receptionist");
        return jsonResponse(200, { data: await bookingService.getBooking(id) });
      }

      if (confirmationCode) {
        return jsonResponse(200, {
          data: await bookingService.getBookingByConfirmationCode(confirmationCode),
        });
      }

      requireStaff(event, "receptionist");
      return jsonResponse(200, {
        data: await bookingService.listBookings(event.queryStringParameters || {}),
      });
    }

    if (event.httpMethod === "POST") {
      const payload = parseJsonBody(event);
      const validation = validateBookingPayload(payload);

      if (!validation.valid) {
        throw badRequest(validation.message);
      }

      return jsonResponse(201, { data: await bookingService.createBooking(payload) });
    }

    if (event.httpMethod === "PATCH") {
      requireStaff(event, "receptionist");

      if (!id) {
        throw badRequest("Booking id is required.");
      }

      const payload = parseJsonBody(event);

      if (payload.status !== undefined) {
        const validation = validateStatusPayload(payload);

        if (!validation.valid) {
          throw badRequest(validation.message);
        }
      }

      return jsonResponse(200, { data: await bookingService.updateBooking(id, payload) });
    }

    if (event.httpMethod === "DELETE") {
      requireStaff(event, "manager");

      if (!id) {
        throw badRequest("Booking id is required.");
      }

      return jsonResponse(200, { data: await bookingService.deleteBooking(id) });
    }

    throw badRequest("Unsupported booking method.");
  } catch (error) {
    return handleError(error, "Unable to process booking request.");
  }
};
