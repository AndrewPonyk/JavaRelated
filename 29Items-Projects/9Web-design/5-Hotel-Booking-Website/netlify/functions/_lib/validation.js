const { assertDateRange } = require("./dateUtils");

const validRoomTypes = new Set(["standard", "deluxe", "suite"]);
const validRoomStatuses = new Set(["available", "maintenance", "inactive"]);
const validBookingStatuses = new Set([
  "pending_confirmation",
  "confirmed",
  "checked_in",
  "completed",
  "cancelled",
]);
const validStaffRoles = new Set(["manager", "receptionist", "readonly"]);
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function hasText(value) {
  return typeof value === "string" && value.trim().length > 0;
}

function asArray(value) {
  return Array.isArray(value) ? value.filter((item) => typeof item === "string") : [];
}

function validEmail(value) {
  return typeof value === "string" && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

function isUuid(value) {
  return typeof value === "string" && uuidPattern.test(value);
}

function validateHotelPayload(payload, partial = false) {
  const required = ["name", "slug", "city", "address"];

  for (const field of required) {
    if (!partial && !hasText(payload[field])) {
      return { valid: false, message: `${field} is required.` };
    }
  }

  if (payload.slug !== undefined && !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(payload.slug)) {
    return { valid: false, message: "Hotel slug must be lowercase kebab-case." };
  }

  if (payload.latitude !== undefined && payload.latitude !== null && Number.isNaN(Number(payload.latitude))) {
    return { valid: false, message: "Latitude must be numeric." };
  }

  if (payload.longitude !== undefined && payload.longitude !== null && Number.isNaN(Number(payload.longitude))) {
    return { valid: false, message: "Longitude must be numeric." };
  }

  return { valid: true };
}

function validateRoomPayload(payload, partial = false) {
  if (!partial && !isUuid(payload.hotelId)) {
    return { valid: false, message: "Valid hotelId is required." };
  }

  if (payload.hotelId !== undefined && !isUuid(payload.hotelId)) {
    return { valid: false, message: "Valid hotelId is required." };
  }

  if (!partial && !hasText(payload.roomNumber)) {
    return { valid: false, message: "roomNumber is required." };
  }

  if (!partial && !validRoomTypes.has(payload.roomType)) {
    return { valid: false, message: "roomType is not supported." };
  }

  if (payload.roomType !== undefined && !validRoomTypes.has(payload.roomType)) {
    return { valid: false, message: "roomType is not supported." };
  }

  if (payload.status !== undefined && !validRoomStatuses.has(payload.status)) {
    return { valid: false, message: "room status is not supported." };
  }

  if (
    payload.capacity !== undefined &&
    (!Number.isInteger(Number(payload.capacity)) || Number(payload.capacity) < 1 || Number(payload.capacity) > 12)
  ) {
    return { valid: false, message: "capacity must be between 1 and 12." };
  }

  if (
    payload.basePriceCents !== undefined &&
    (!Number.isInteger(Number(payload.basePriceCents)) || Number(payload.basePriceCents) < 0)
  ) {
    return { valid: false, message: "basePriceCents must be a positive integer." };
  }

  return { valid: true };
}

function validateGuestPayload(payload, partial = false) {
  if (!partial && !hasText(payload.fullName)) {
    return { valid: false, message: "fullName is required." };
  }

  if (!partial && !validEmail(payload.email)) {
    return { valid: false, message: "Valid email is required." };
  }

  if (payload.email !== undefined && !validEmail(payload.email)) {
    return { valid: false, message: "Valid email is required." };
  }

  return { valid: true };
}

function validateBookingPayload(payload) {
  const dateError = assertDateRange(payload.checkIn, payload.checkOut);

  if (dateError) {
    return { valid: false, message: dateError };
  }

  if (!Number.isInteger(Number(payload.guests)) || Number(payload.guests) < 1 || Number(payload.guests) > 12) {
    return { valid: false, message: "Guest count must be between 1 and 12." };
  }

  if (payload.roomType !== undefined && payload.roomType !== "" && !validRoomTypes.has(payload.roomType)) {
    return { valid: false, message: "Room type is not supported." };
  }

  if (payload.roomId !== undefined && !isUuid(payload.roomId)) {
    return { valid: false, message: "Valid roomId is required." };
  }

  if (payload.hotelId !== undefined && !isUuid(payload.hotelId)) {
    return { valid: false, message: "Valid hotelId is required." };
  }

  if (!payload.roomId && !payload.roomType) {
    return { valid: false, message: "roomId or roomType is required." };
  }

  if (!validEmail(payload.guestEmail)) {
    return { valid: false, message: "Valid guest email is required." };
  }

  if (!hasText(payload.guestName)) {
    return { valid: false, message: "Guest name is required." };
  }

  return { valid: true };
}

function validateAvailabilityPayload(payload) {
  const dateError = assertDateRange(payload.checkIn, payload.checkOut);

  if (dateError) {
    return { valid: false, message: dateError };
  }

  if (!Number.isInteger(Number(payload.guests)) || Number(payload.guests) < 1 || Number(payload.guests) > 12) {
    return { valid: false, message: "Guest count must be between 1 and 12." };
  }

  if (payload.roomType && !validRoomTypes.has(payload.roomType)) {
    return { valid: false, message: "Room type is not supported." };
  }

  if (payload.hotelId !== undefined && payload.hotelId !== "" && !isUuid(payload.hotelId)) {
    return { valid: false, message: "Valid hotelId is required." };
  }

  return { valid: true };
}

function validateStatusPayload(payload) {
  if (!validBookingStatuses.has(payload.status)) {
    return { valid: false, message: "Booking status is not supported." };
  }

  return { valid: true };
}

function validateStaffUserPayload(payload, partial = false) {
  if (!partial && !validEmail(payload.email)) {
    return { valid: false, message: "Valid staff email is required." };
  }

  if (payload.email !== undefined && !validEmail(payload.email)) {
    return { valid: false, message: "Valid staff email is required." };
  }

  if (!partial && !hasText(payload.displayName)) {
    return { valid: false, message: "displayName is required." };
  }

  if (payload.role !== undefined && !validStaffRoles.has(payload.role)) {
    return { valid: false, message: "Staff role is not supported." };
  }

  return { valid: true };
}

module.exports = {
  asArray,
  isUuid,
  validateAvailabilityPayload,
  validateBookingPayload,
  validateGuestPayload,
  validateHotelPayload,
  validateRoomPayload,
  validateStaffUserPayload,
  validateStatusPayload,
};
