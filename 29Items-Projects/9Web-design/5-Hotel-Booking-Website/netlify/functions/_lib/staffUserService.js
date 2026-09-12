const db = require("./db");
const { conflict, notFound } = require("./errors");
const { appendPagination } = require("./pagination");
const serialize = require("./serializers");

async function listStaffUsers(filters = {}, database = db) {
  const values = [];
  const where = [];

  if (filters.role) {
    values.push(filters.role);
    where.push(`role = $${values.length}`);
  }

  const result = await database.query(
    `
      SELECT *
      FROM staff_users
      ${where.length ? `WHERE ${where.join(" AND ")}` : ""}
      ORDER BY display_name ASC
      ${appendPagination(values, filters)}
    `,
    values,
  );

  return result.rows.map(serialize.staffUser);
}

async function getStaffUser(id, database = db) {
  const result = await database.query("SELECT * FROM staff_users WHERE id = $1", [id]);

  if (result.rowCount === 0) {
    throw notFound("Staff user");
  }

  return serialize.staffUser(result.rows[0]);
}

async function createStaffUser(payload, database = db) {
  try {
    const result = await database.query(
      `
        INSERT INTO staff_users (email, role, display_name, active)
        VALUES (LOWER($1), $2, $3, COALESCE($4, TRUE))
        RETURNING *
      `,
      [payload.email.trim(), payload.role || "receptionist", payload.displayName.trim(), payload.active],
    );

    return serialize.staffUser(result.rows[0]);
  } catch (error) {
    if (error.code === "23505") {
      throw conflict("Staff email already exists.");
    }
    throw error;
  }
}

async function updateStaffUser(id, payload, database = db) {
  const current = await getStaffUser(id, database);

  try {
    const result = await database.query(
      `
        UPDATE staff_users
        SET email = LOWER($2),
            role = $3,
            display_name = $4,
            active = $5,
            updated_at = NOW()
        WHERE id = $1
        RETURNING *
      `,
      [
        id,
        payload.email ?? current.email,
        payload.role ?? current.role,
        payload.displayName ?? current.displayName,
        payload.active ?? current.active,
      ],
    );

    return serialize.staffUser(result.rows[0]);
  } catch (error) {
    if (error.code === "23505") {
      throw conflict("Staff email already exists.");
    }
    throw error;
  }
}

async function deleteStaffUser(id, database = db) {
  const result = await database.query("DELETE FROM staff_users WHERE id = $1 RETURNING *", [id]);

  if (result.rowCount === 0) {
    throw notFound("Staff user");
  }

  return serialize.staffUser(result.rows[0]);
}

module.exports = {
  createStaffUser,
  deleteStaffUser,
  getStaffUser,
  listStaffUsers,
  updateStaffUser,
};
