const db = require("./db");
const { conflict, notFound } = require("./errors");
const { appendPagination } = require("./pagination");
const serialize = require("./serializers");
const { asArray } = require("./validation");

const ROOM_SELECT = `
  SELECT r.*, h.name AS hotel_name, h.city, h.image_url AS hotel_image_url
  FROM rooms r
  JOIN hotels h ON h.id = r.hotel_id
`;

async function listRooms(filters = {}, database = db) {
  const values = [];
  const where = [];

  if (filters.hotelId) {
    values.push(filters.hotelId);
    where.push(`r.hotel_id = $${values.length}`);
  }

  if (filters.status) {
    values.push(filters.status);
    where.push(`r.status = $${values.length}`);
  }

  const result = await database.query(
    `
      ${ROOM_SELECT}
      ${where.length ? `WHERE ${where.join(" AND ")}` : ""}
      ORDER BY h.name ASC, r.room_number ASC
      ${appendPagination(values, filters)}
    `,
    values,
  );

  return result.rows.map(serialize.room);
}

async function getRoom(id, database = db) {
  const result = await database.query(`${ROOM_SELECT} WHERE r.id = $1`, [id]);

  if (result.rowCount === 0) {
    throw notFound("Room");
  }

  return serialize.room(result.rows[0]);
}

async function createRoom(payload, database = db) {
  try {
    const result = await database.query(
      `
        INSERT INTO rooms
          (hotel_id, room_type, room_number, capacity, base_price_cents, status, image_url, amenities)
        VALUES ($1, $2, $3, $4, $5, COALESCE($6, 'available'), $7, $8::jsonb)
        RETURNING *
      `,
      [
        payload.hotelId,
        payload.roomType,
        payload.roomNumber,
        Number(payload.capacity),
        Number(payload.basePriceCents),
        payload.status,
        payload.imageUrl || "",
        JSON.stringify(asArray(payload.amenities)),
      ],
    );

    return getRoom(result.rows[0].id, database);
  } catch (error) {
    if (error.code === "23505") {
      throw conflict("Room number already exists for this hotel.");
    }
    if (error.code === "23503") {
      throw notFound("Hotel");
    }
    throw error;
  }
}

async function updateRoom(id, payload, database = db) {
  const current = await getRoom(id, database);

  try {
    const result = await database.query(
      `
        UPDATE rooms
        SET
          hotel_id = $2,
          room_type = $3,
          room_number = $4,
          capacity = $5,
          base_price_cents = $6,
          status = $7,
          image_url = $8,
          amenities = $9::jsonb,
          updated_at = NOW()
        WHERE id = $1
        RETURNING id
      `,
      [
        id,
        payload.hotelId ?? current.hotelId,
        payload.roomType ?? current.roomType,
        payload.roomNumber ?? current.roomNumber,
        payload.capacity === undefined ? current.capacity : Number(payload.capacity),
        payload.basePriceCents === undefined ? current.basePriceCents : Number(payload.basePriceCents),
        payload.status ?? current.status,
        payload.imageUrl ?? current.imageUrl,
        JSON.stringify(payload.amenities === undefined ? current.amenities : asArray(payload.amenities)),
      ],
    );

    return getRoom(result.rows[0].id, database);
  } catch (error) {
    if (error.code === "23505") {
      throw conflict("Room number already exists for this hotel.");
    }
    if (error.code === "23503") {
      throw notFound("Hotel");
    }
    throw error;
  }
}

async function deleteRoom(id, database = db) {
  const result = await database.query("DELETE FROM rooms WHERE id = $1 RETURNING *", [id]);

  if (result.rowCount === 0) {
    throw notFound("Room");
  }

  return serialize.room(result.rows[0]);
}

module.exports = {
  createRoom,
  deleteRoom,
  getRoom,
  listRooms,
  updateRoom,
};
