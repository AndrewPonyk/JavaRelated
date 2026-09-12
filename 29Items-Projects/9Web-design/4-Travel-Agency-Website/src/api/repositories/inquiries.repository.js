const { query } = require("../db/pool");

function toInquiry(row) {
  return {
    id: row.id,
    tourId: row.tour_id,
    name: row.name,
    email: row.email,
    destination: row.destination,
    budget: row.budget,
    message: row.message,
    consent: row.consent,
    status: row.status,
    createdAt: row.created_at,
    updatedAt: row.updated_at
  };
}

async function listInquiries(filters = {}) {
  const values = [];
  const clauses = [];

  if (filters.status) {
    values.push(filters.status);
    clauses.push(`status = $${values.length}`);
  }

  const where = clauses.length > 0 ? `WHERE ${clauses.join(" AND ")}` : "";
  const countResult = await query(`SELECT COUNT(*) AS total FROM inquiries ${where}`, values);
  const pageValues = [...values, filters.limit, filters.offset];
  const result = await query(
    `SELECT * FROM inquiries ${where} ORDER BY created_at DESC LIMIT $${pageValues.length - 1} OFFSET $${pageValues.length}`,
    pageValues
  );

  return {
    items: result.rows.map(toInquiry),
    total: Number(countResult.rows[0].total)
  };
}

async function getInquiryById(id) {
  const result = await query("SELECT * FROM inquiries WHERE id = $1", [id]);
  return result.rows[0] ? toInquiry(result.rows[0]) : null;
}

async function createInquiry(payload) {
  const result = await query(
    `INSERT INTO inquiries (tour_id, name, email, destination, budget, message, consent, status)
     VALUES ($1, $2, $3, $4, $5, $6, $7, 'new')
     RETURNING *`,
    [
      payload.tourId || null,
      payload.name,
      payload.email,
      payload.destination,
      payload.budget || null,
      payload.message,
      payload.consent
    ]
  );

  return toInquiry(result.rows[0]);
}

async function updateInquiry(id, payload) {
  const fields = [];
  const values = [];
  const mappings = {
    tourId: "tour_id",
    name: "name",
    email: "email",
    destination: "destination",
    budget: "budget",
    message: "message",
    consent: "consent",
    status: "status"
  };

  Object.entries(mappings).forEach(([key, column]) => {
    if (payload[key] !== undefined) {
      values.push(payload[key]);
      fields.push(`${column} = $${values.length}`);
    }
  });

  if (fields.length === 0) {
    return getInquiryById(id);
  }

  values.push(id);
  const result = await query(
    `UPDATE inquiries
     SET ${fields.join(", ")}, updated_at = NOW()
     WHERE id = $${values.length}
     RETURNING *`,
    values
  );

  return result.rows[0] ? toInquiry(result.rows[0]) : null;
}

async function deleteInquiry(id) {
  const result = await query("DELETE FROM inquiries WHERE id = $1 RETURNING id", [id]);
  return result.rowCount > 0;
}

module.exports = { createInquiry, deleteInquiry, getInquiryById, listInquiries, updateInquiry };
