const db = require("./db");
const { conflict, notFound } = require("./errors");
const { appendPagination } = require("./pagination");
const serialize = require("./serializers");

async function listGuests(filters = {}, database = db) {
  const values = [];
  const where = [];

  if (filters.email) {
    values.push(`%${filters.email}%`);
    where.push(`email ILIKE $${values.length}`);
  }

  const result = await database.query(
    `
      SELECT *
      FROM guests
      ${where.length ? `WHERE ${where.join(" AND ")}` : ""}
      ORDER BY created_at DESC
      ${appendPagination(values, filters)}
    `,
    values,
  );

  return result.rows.map(serialize.guest);
}

async function getGuest(id, database = db) {
  const result = await database.query("SELECT * FROM guests WHERE id = $1", [id]);

  if (result.rowCount === 0) {
    throw notFound("Guest");
  }

  return serialize.guest(result.rows[0]);
}

async function upsertGuest(payload, database = db) {
  const result = await database.query(
    `
      INSERT INTO guests (full_name, email, phone)
      VALUES ($1, LOWER($2), $3)
      ON CONFLICT (email) DO UPDATE
      SET full_name = EXCLUDED.full_name,
          phone = COALESCE(EXCLUDED.phone, guests.phone),
          updated_at = NOW()
      RETURNING *
    `,
    [payload.fullName.trim(), payload.email.trim(), payload.phone || null],
  );

  return serialize.guest(result.rows[0]);
}

async function createGuest(payload, database = db) {
  try {
    const result = await database.query(
      `
        INSERT INTO guests (full_name, email, phone)
        VALUES ($1, LOWER($2), $3)
        RETURNING *
      `,
      [payload.fullName.trim(), payload.email.trim(), payload.phone || null],
    );

    return serialize.guest(result.rows[0]);
  } catch (error) {
    if (error.code === "23505") {
      throw conflict("Guest email already exists.");
    }
    throw error;
  }
}

async function updateGuest(id, payload, database = db) {
  const current = await getGuest(id, database);

  try {
    const result = await database.query(
      `
        UPDATE guests
        SET full_name = $2,
            email = LOWER($3),
            phone = $4,
            updated_at = NOW()
        WHERE id = $1
        RETURNING *
      `,
      [
        id,
        payload.fullName ?? current.fullName,
        payload.email ?? current.email,
        payload.phone === undefined ? current.phone : payload.phone,
      ],
    );

    return serialize.guest(result.rows[0]);
  } catch (error) {
    if (error.code === "23505") {
      throw conflict("Guest email already exists.");
    }
    throw error;
  }
}

async function deleteGuest(id, database = db) {
  const result = await database.query("DELETE FROM guests WHERE id = $1 RETURNING *", [id]);

  if (result.rowCount === 0) {
    throw notFound("Guest");
  }

  return serialize.guest(result.rows[0]);
}

module.exports = {
  createGuest,
  deleteGuest,
  getGuest,
  listGuests,
  updateGuest,
  upsertGuest,
};
