const db = require("./db");
const { conflict, notFound } = require("./errors");
const { appendPagination } = require("./pagination");
const serialize = require("./serializers");
const { asArray } = require("./validation");

async function listHotels(filters = {}, database = db) {
  const values = [];
  const where = [];

  if (filters.city) {
    values.push(`%${filters.city}%`);
    where.push(`city ILIKE $${values.length}`);
  }

  if (filters.active !== undefined) {
    values.push(filters.active === "false" ? false : Boolean(filters.active));
    where.push(`active = $${values.length}`);
  }

  const result = await database.query(
    `
      SELECT *
      FROM hotels
      ${where.length ? `WHERE ${where.join(" AND ")}` : ""}
      ORDER BY city ASC, name ASC
      ${appendPagination(values, filters)}
    `,
    values,
  );

  return result.rows.map(serialize.hotel);
}

async function getHotel(id, database = db) {
  const result = await database.query("SELECT * FROM hotels WHERE id = $1", [id]);

  if (result.rowCount === 0) {
    throw notFound("Hotel");
  }

  return serialize.hotel(result.rows[0]);
}

async function createHotel(payload, database = db) {
  try {
    const result = await database.query(
      `
        INSERT INTO hotels
          (name, slug, city, address, description, latitude, longitude, image_url, amenities, active)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9::jsonb, COALESCE($10, TRUE))
        RETURNING *
      `,
      [
        payload.name.trim(),
        payload.slug.trim(),
        payload.city.trim(),
        payload.address.trim(),
        payload.description || "",
        payload.latitude ?? null,
        payload.longitude ?? null,
        payload.imageUrl || "",
        JSON.stringify(asArray(payload.amenities)),
        payload.active,
      ],
    );

    return serialize.hotel(result.rows[0]);
  } catch (error) {
    if (error.code === "23505") {
      throw conflict("Hotel slug already exists.");
    }
    throw error;
  }
}

async function updateHotel(id, payload, database = db) {
  try {
    const current = await getHotel(id, database);
    const result = await database.query(
      `
        UPDATE hotels
        SET
          name = $2,
          slug = $3,
          city = $4,
          address = $5,
          description = $6,
          latitude = $7,
          longitude = $8,
          image_url = $9,
          amenities = $10::jsonb,
          active = $11,
          updated_at = NOW()
        WHERE id = $1
        RETURNING *
      `,
      [
        id,
        payload.name ?? current.name,
        payload.slug ?? current.slug,
        payload.city ?? current.city,
        payload.address ?? current.address,
        payload.description ?? current.description,
        payload.latitude ?? current.latitude,
        payload.longitude ?? current.longitude,
        payload.imageUrl ?? current.imageUrl,
        JSON.stringify(payload.amenities === undefined ? current.amenities : asArray(payload.amenities)),
        payload.active ?? current.active,
      ],
    );

    return serialize.hotel(result.rows[0]);
  } catch (error) {
    if (error.code === "23505") {
      throw conflict("Hotel slug already exists.");
    }
    throw error;
  }
}

async function deleteHotel(id, database = db) {
  const result = await database.query("DELETE FROM hotels WHERE id = $1 RETURNING *", [id]);

  if (result.rowCount === 0) {
    throw notFound("Hotel");
  }

  return serialize.hotel(result.rows[0]);
}

module.exports = {
  createHotel,
  deleteHotel,
  getHotel,
  listHotels,
  updateHotel,
};
