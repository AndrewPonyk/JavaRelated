const { query } = require("../db/pool");

function toDestination(row) {
  return {
    id: row.id,
    slug: row.slug,
    name: row.name,
    region: row.region,
    latitude: Number(row.latitude),
    longitude: Number(row.longitude),
    description: row.description,
    image: row.image_url,
    createdAt: row.created_at,
    updatedAt: row.updated_at
  };
}

async function listDestinations(filters = {}) {
  const values = [];
  const clauses = [];

  if (filters.region) {
    values.push(filters.region);
    clauses.push(`region = $${values.length}`);
  }

  const where = clauses.length > 0 ? `WHERE ${clauses.join(" AND ")}` : "";
  const countResult = await query(`SELECT COUNT(*) AS total FROM destinations ${where}`, values);
  const pageValues = [...values, filters.limit, filters.offset];
  const result = await query(
    `SELECT * FROM destinations ${where} ORDER BY name ASC LIMIT $${pageValues.length - 1} OFFSET $${pageValues.length}`,
    pageValues
  );

  return {
    items: result.rows.map(toDestination),
    total: Number(countResult.rows[0].total)
  };
}

async function getDestinationById(id) {
  const result = await query("SELECT * FROM destinations WHERE id = $1", [id]);
  return result.rows[0] ? toDestination(result.rows[0]) : null;
}

async function getDestinationBySlug(slug) {
  const result = await query("SELECT * FROM destinations WHERE slug = $1", [slug]);
  return result.rows[0] ? toDestination(result.rows[0]) : null;
}

async function createDestination(payload) {
  const result = await query(
    `INSERT INTO destinations (slug, name, region, latitude, longitude, description, image_url)
     VALUES ($1, $2, $3, $4, $5, $6, $7)
     RETURNING *`,
    [payload.slug, payload.name, payload.region, payload.latitude, payload.longitude, payload.description, payload.image]
  );

  return toDestination(result.rows[0]);
}

async function updateDestination(id, payload) {
  const fields = [];
  const values = [];
  const mappings = {
    slug: "slug",
    name: "name",
    region: "region",
    latitude: "latitude",
    longitude: "longitude",
    description: "description",
    image: "image_url"
  };

  Object.entries(mappings).forEach(([key, column]) => {
    if (payload[key] !== undefined) {
      values.push(payload[key]);
      fields.push(`${column} = $${values.length}`);
    }
  });

  if (fields.length === 0) {
    return getDestinationById(id);
  }

  values.push(id);
  const result = await query(
    `UPDATE destinations
     SET ${fields.join(", ")}, updated_at = NOW()
     WHERE id = $${values.length}
     RETURNING *`,
    values
  );

  return result.rows[0] ? toDestination(result.rows[0]) : null;
}

async function deleteDestination(id) {
  const result = await query("DELETE FROM destinations WHERE id = $1 RETURNING id", [id]);
  return result.rowCount > 0;
}

module.exports = {
  createDestination,
  deleteDestination,
  getDestinationById,
  getDestinationBySlug,
  listDestinations,
  updateDestination
};
