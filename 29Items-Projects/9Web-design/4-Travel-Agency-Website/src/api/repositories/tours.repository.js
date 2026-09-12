const { query } = require("../db/pool");

function toTour(row) {
  return {
    id: row.id,
    slug: row.slug,
    title: row.title,
    region: row.region,
    regionLabel: row.region_label,
    durationDays: row.duration_days,
    price: Math.round(row.price_cents / 100),
    difficulty: row.difficulty,
    featured: row.featured,
    summary: row.summary,
    image: row.image_url,
    createdAt: row.created_at,
    updatedAt: row.updated_at
  };
}

async function listTours(filters = {}) {
  const clauses = [];
  const values = [];

  addClause(clauses, values, filters.region, "region =");
  addClause(clauses, values, filters.difficulty, "difficulty =");

  if (filters.featured !== undefined) {
    values.push(filters.featured);
    clauses.push(`featured = $${values.length}`);
  }

  if (filters.maxPrice) {
    values.push(Number(filters.maxPrice) * 100);
    clauses.push(`price_cents <= $${values.length}`);
  }

  if (filters.maxDuration) {
    values.push(Number(filters.maxDuration));
    clauses.push(`duration_days <= $${values.length}`);
  }

  const orderBy = getSortClause(filters.sort);
  const where = clauses.length > 0 ? `WHERE ${clauses.join(" AND ")}` : "";
  const countResult = await query(`SELECT COUNT(*) AS total FROM tours ${where}`, values);
  const pageValues = [...values, filters.limit, filters.offset];
  const result = await query(
    `SELECT * FROM tours ${where} ${orderBy} LIMIT $${pageValues.length - 1} OFFSET $${pageValues.length}`,
    pageValues
  );

  return {
    items: result.rows.map(toTour),
    total: Number(countResult.rows[0].total)
  };
}

async function getTourById(id) {
  const result = await query("SELECT * FROM tours WHERE id = $1", [id]);
  return result.rows[0] ? toTour(result.rows[0]) : null;
}

async function getTourBySlug(slug) {
  const result = await query("SELECT * FROM tours WHERE slug = $1", [slug]);
  return result.rows[0] ? toTour(result.rows[0]) : null;
}

async function createTour(payload) {
  const result = await query(
    `INSERT INTO tours
      (slug, title, region, region_label, duration_days, price_cents, difficulty, featured, summary, image_url)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
     RETURNING *`,
    [
      payload.slug,
      payload.title,
      payload.region,
      payload.regionLabel,
      payload.durationDays,
      dollarsToCents(payload.price),
      payload.difficulty,
      payload.featured,
      payload.summary,
      payload.image
    ]
  );

  return toTour(result.rows[0]);
}

async function updateTour(id, payload) {
  const fields = [];
  const values = [];
  const mappings = {
    slug: "slug",
    title: "title",
    region: "region",
    regionLabel: "region_label",
    durationDays: "duration_days",
    price: "price_cents",
    difficulty: "difficulty",
    featured: "featured",
    summary: "summary",
    image: "image_url"
  };

  Object.entries(mappings).forEach(([key, column]) => {
    if (payload[key] !== undefined) {
      values.push(key === "price" ? dollarsToCents(payload[key]) : payload[key]);
      fields.push(`${column} = $${values.length}`);
    }
  });

  if (fields.length === 0) {
    return getTourById(id);
  }

  values.push(id);
  const result = await query(
    `UPDATE tours
     SET ${fields.join(", ")}, updated_at = NOW()
     WHERE id = $${values.length}
     RETURNING *`,
    values
  );

  return result.rows[0] ? toTour(result.rows[0]) : null;
}

async function deleteTour(id) {
  const result = await query("DELETE FROM tours WHERE id = $1 RETURNING id", [id]);
  return result.rowCount > 0;
}

function addClause(clauses, values, value, expression) {
  if (value !== undefined && value !== "") {
    values.push(value);
    clauses.push(`${expression} $${values.length}`);
  }
}

function dollarsToCents(value) {
  return Math.round(Number(value) * 100);
}

function getSortClause(sort) {
  switch (sort) {
    case "price-asc":
      return "ORDER BY price_cents ASC, id ASC";
    case "price-desc":
      return "ORDER BY price_cents DESC, id ASC";
    case "duration-asc":
      return "ORDER BY duration_days ASC, id ASC";
    case "duration-desc":
      return "ORDER BY duration_days DESC, id ASC";
    default:
      return "ORDER BY featured DESC, title ASC";
  }
}

module.exports = { createTour, deleteTour, getTourById, getTourBySlug, listTours, updateTour };
