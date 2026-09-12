const db = require("./db");
const { calculateNights } = require("./dateUtils");
const { appendPagination } = require("./pagination");
const serialize = require("./serializers");

const ACTIVE_BOOKING_STATUSES = ["pending_confirmation", "confirmed", "checked_in"];
const TAX_RATE = 0.12;

function buildAvailabilityFilters(params) {
  const values = [params.checkIn, params.checkOut, params.guests];
  const filters = [
    "h.active = TRUE",
    "r.status = 'available'",
    "r.capacity >= $3",
    "b.id IS NULL",
  ];

  if (params.roomType) {
    values.push(params.roomType);
    filters.push(`r.room_type = $${values.length}`);
  }

  if (params.city) {
    values.push(`%${params.city}%`);
    filters.push(`h.city ILIKE $${values.length}`);
  }

  if (params.hotelId) {
    values.push(params.hotelId);
    filters.push(`h.id = $${values.length}`);
  }

  return { filters, values };
}

function calculatePrice(basePriceCents, nights) {
  const nightlySubtotalCents = basePriceCents * nights;
  const taxesCents = Math.round(nightlySubtotalCents * TAX_RATE);

  return {
    nightlySubtotalCents,
    taxesCents,
    totalPriceCents: nightlySubtotalCents + taxesCents,
  };
}

async function findAvailableRooms(params, database = db) {
  const nights = calculateNights(params.checkIn, params.checkOut);
  const { filters, values } = buildAvailabilityFilters(params);
  const result = await database.query(
    `
      SELECT
        r.*,
        h.name AS hotel_name,
        h.city,
        h.image_url AS hotel_image_url
      FROM rooms r
      JOIN hotels h ON h.id = r.hotel_id
      LEFT JOIN bookings b ON b.room_id = r.id
        AND b.status IN ('pending_confirmation', 'confirmed', 'checked_in')
        AND b.check_in < $2::date
        AND b.check_out > $1::date
      WHERE ${filters.join(" AND ")}
      ORDER BY r.base_price_cents ASC, h.name ASC
      ${appendPagination(values, params)}
    `,
    values,
  );

  return result.rows.map((row) => {
    const serializedRoom = serialize.room(row);
    const price = calculatePrice(row.base_price_cents, nights);

    return {
      ...serializedRoom,
      nights,
      ...price,
      totalPrice: serialize.centsToDollars(price.totalPriceCents),
    };
  });
}

async function assertRoomAvailable(client, roomId, checkIn, checkOut, guests) {
  const result = await client.query(
    `
      SELECT r.*, h.name AS hotel_name, h.city, h.image_url AS hotel_image_url
      FROM rooms r
      JOIN hotels h ON h.id = r.hotel_id
      WHERE r.id = $1
        AND r.status = 'available'
        AND h.active = TRUE
        AND r.capacity >= $2
      FOR UPDATE
    `,
    [roomId, guests],
  );

  if (result.rowCount === 0) {
    return null;
  }

  const overlap = await client.query(
    `
      SELECT id
      FROM bookings
      WHERE room_id = $1
        AND status IN ('pending_confirmation', 'confirmed', 'checked_in')
        AND check_in < $3::date
        AND check_out > $2::date
      LIMIT 1
    `,
    [roomId, checkIn, checkOut],
  );

  return overlap.rowCount === 0 ? result.rows[0] : null;
}

module.exports = {
  ACTIVE_BOOKING_STATUSES,
  TAX_RATE,
  assertRoomAvailable,
  calculatePrice,
  findAvailableRooms,
};
