const availabilityService = require("./availabilityService");
const db = require("./db");
const { calculateNights } = require("./dateUtils");
const { conflict, notFound } = require("./errors");
const { upsertGuest } = require("./guestService");
const { appendPagination } = require("./pagination");
const serialize = require("./serializers");

function buildConfirmationCode() {
  const random = Math.random().toString(36).slice(2, 7).toUpperCase();
  return `HB-${Date.now().toString(36).toUpperCase()}-${random}`;
}

function bookingSelect(whereClause = "", orderClause = "ORDER BY b.created_at DESC") {
  return `
    SELECT
      b.*,
      g.email AS guest_email,
      g.full_name AS guest_name,
      h.name AS hotel_name,
      r.room_number,
      r.room_type
    FROM bookings b
    JOIN guests g ON g.id = b.guest_id
    JOIN rooms r ON r.id = b.room_id
    JOIN hotels h ON h.id = r.hotel_id
    ${whereClause}
    ${orderClause}
  `;
}

async function listBookings(filters = {}, database = db) {
  const values = [];
  const where = [];

  if (filters.status) {
    values.push(filters.status);
    where.push(`b.status = $${values.length}`);
  }

  if (filters.guestEmail) {
    values.push(`%${filters.guestEmail}%`);
    where.push(`g.email ILIKE $${values.length}`);
  }

  const result = await database.query(
    bookingSelect(
      where.length ? `WHERE ${where.join(" AND ")}` : "",
      `ORDER BY b.created_at DESC ${appendPagination(values, filters)}`,
    ),
    values,
  );

  return result.rows.map(serialize.booking);
}

async function getBooking(id, database = db) {
  const result = await database.query(bookingSelect("WHERE b.id = $1", ""), [id]);

  if (result.rowCount === 0) {
    throw notFound("Booking");
  }

  return serialize.booking(result.rows[0]);
}

async function getBookingByConfirmationCode(code, database = db) {
  const result = await database.query(bookingSelect("WHERE b.confirmation_code = $1", ""), [code]);

  if (result.rowCount === 0) {
    throw notFound("Booking");
  }

  return serialize.booking(result.rows[0]);
}

async function pickAvailableRoom(payload, client) {
  if (payload.roomId) {
    return availabilityService.assertRoomAvailable(
      client,
      payload.roomId,
      payload.checkIn,
      payload.checkOut,
      Number(payload.guests),
    );
  }

  const rooms = await availabilityService.findAvailableRooms(
    {
      checkIn: payload.checkIn,
      checkOut: payload.checkOut,
      guests: Number(payload.guests),
      roomType: payload.roomType,
      city: payload.city,
      hotelId: payload.hotelId,
    },
    client,
  );

  if (!rooms.length) {
    return null;
  }

  return availabilityService.assertRoomAvailable(
    client,
    rooms[0].id,
    payload.checkIn,
    payload.checkOut,
    Number(payload.guests),
  );
}

async function createBooking(payload, database = db) {
  return database.withTransaction(async (client) => {
    const selectedRoom = await pickAvailableRoom(payload, client);

    if (!selectedRoom) {
      throw conflict("No matching room is available for the selected dates.");
    }

    const nights = calculateNights(payload.checkIn, payload.checkOut);
    const price = availabilityService.calculatePrice(selectedRoom.base_price_cents, nights);
    const guest = await upsertGuest(
      {
        fullName: payload.guestName,
        email: payload.guestEmail,
        phone: payload.guestPhone || null,
      },
      client,
    );

    const result = await client.query(
      `
        INSERT INTO bookings
          (
            confirmation_code,
            room_id,
            guest_id,
            check_in,
            check_out,
            guests_count,
            nightly_subtotal_cents,
            taxes_cents,
            total_price_cents,
            status,
            special_requests
          )
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, 'confirmed', $10)
        RETURNING id
      `,
      [
        buildConfirmationCode(),
        selectedRoom.id,
        guest.id,
        payload.checkIn,
        payload.checkOut,
        Number(payload.guests),
        price.nightlySubtotalCents,
        price.taxesCents,
        price.totalPriceCents,
        payload.specialRequests || "",
      ],
    );

    return getBooking(result.rows[0].id, client);
  });
}

async function updateBookingStatus(bookingId, status, database = db) {
  const result = await database.query(
    `
      UPDATE bookings
      SET status = $2,
          updated_at = NOW()
      WHERE id = $1
      RETURNING id
    `,
    [bookingId, status],
  );

  if (result.rowCount === 0) {
    throw notFound("Booking");
  }

  return getBooking(result.rows[0].id, database);
}

async function updateBooking(bookingId, payload, database = db) {
  const current = await getBooking(bookingId, database);

  if (
    payload.checkIn !== undefined ||
    payload.checkOut !== undefined ||
    payload.roomId !== undefined ||
    payload.guests !== undefined
  ) {
    throw conflict("Use cancellation and a new booking to change dates, room, or guest count.");
  }

  const result = await database.query(
    `
      UPDATE bookings
      SET status = $2,
          special_requests = $3,
          updated_at = NOW()
      WHERE id = $1
      RETURNING id
    `,
    [
      bookingId,
      payload.status ?? current.status,
      payload.specialRequests ?? current.specialRequests,
    ],
  );

  return getBooking(result.rows[0].id, database);
}

async function cancelBooking(bookingId, database = db) {
  return updateBookingStatus(bookingId, "cancelled", database);
}

async function deleteBooking(bookingId, database = db) {
  const result = await database.query("DELETE FROM bookings WHERE id = $1 RETURNING *", [bookingId]);

  if (result.rowCount === 0) {
    throw notFound("Booking");
  }

  return serialize.booking(result.rows[0]);
}

module.exports = {
  buildConfirmationCode,
  calculateNights,
  cancelBooking,
  createBooking,
  deleteBooking,
  getBooking,
  getBookingByConfirmationCode,
  listBookings,
  updateBooking,
  updateBookingStatus,
};
