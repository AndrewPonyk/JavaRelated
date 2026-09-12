import { randomUUID } from 'node:crypto';

const rowFields = `
  id,
  customer_name,
  customer_email,
  reservation_date,
  party_size,
  notes,
  status,
  created_at,
  updated_at
`;

export function createReservationRepository(client) {
  return {
    async list(filters = {}) {
      const where = [];
      const values = [];

      if (filters.status) {
        values.push(filters.status);
        where.push(`status = $${values.length}`);
      }

      if (filters.date) {
        values.push(filters.date);
        where.push(`reservation_date = $${values.length}`);
      }

      const countResult = await client.query(
        `
          SELECT COUNT(*)::int AS total
          FROM reservations
          ${where.length > 0 ? `WHERE ${where.join(' AND ')}` : ''}
        `,
        values
      );

      const paginatedValues = [...values, filters.pageSize, filters.offset];
      const result = await client.query(
        `
          SELECT ${rowFields}
          FROM reservations
          ${where.length > 0 ? `WHERE ${where.join(' AND ')}` : ''}
          ORDER BY reservation_date ASC, created_at ASC
          LIMIT $${paginatedValues.length - 1}
          OFFSET $${paginatedValues.length}
        `,
        paginatedValues
      );

      return {
        reservations: result.rows.map(toReservation),
        total: Number(countResult.rows[0]?.total || 0)
      };
    },

    async findById(id) {
      const result = await client.query(`SELECT ${rowFields} FROM reservations WHERE id = $1`, [id]);
      return result.rows[0] ? toReservation(result.rows[0]) : null;
    },

    async create(input) {
      const id = randomUUID();
      const result = await client.query(
        `
          INSERT INTO reservations (
            id,
            customer_name,
            customer_email,
            reservation_date,
            party_size,
            notes,
            status
          )
          VALUES ($1, $2, $3, $4, $5, $6, $7)
          RETURNING ${rowFields}
        `,
        [
          id,
          input.name,
          input.email,
          input.date,
          input.partySize,
          input.notes || null,
          input.status || 'requested'
        ]
      );

      return toReservation(result.rows[0]);
    },

    async update(id, input) {
      const assignments = [];
      const values = [];
      const fieldMap = {
        name: 'customer_name',
        email: 'customer_email',
        date: 'reservation_date',
        partySize: 'party_size',
        notes: 'notes',
        status: 'status'
      };

      Object.entries(fieldMap).forEach(([key, column]) => {
        if (Object.prototype.hasOwnProperty.call(input, key)) {
          values.push(input[key] === '' ? null : input[key]);
          assignments.push(`${column} = $${values.length}`);
        }
      });

      if (assignments.length === 0) {
        return this.findById(id);
      }

      values.push(id);

      const result = await client.query(
        `
          UPDATE reservations
          SET ${assignments.join(', ')}, updated_at = now()
          WHERE id = $${values.length}
          RETURNING ${rowFields}
        `,
        values
      );

      return result.rows[0] ? toReservation(result.rows[0]) : null;
    },

    async delete(id) {
      const result = await client.query(`DELETE FROM reservations WHERE id = $1 RETURNING ${rowFields}`, [id]);
      return result.rows[0] ? toReservation(result.rows[0]) : null;
    }
  };
}

export function toReservation(row) {
  return {
    id: row.id,
    name: row.customer_name,
    email: row.customer_email,
    date: toDateOnly(row.reservation_date),
    partySize: Number(row.party_size),
    notes: row.notes || '',
    status: row.status,
    createdAt: toIso(row.created_at),
    updatedAt: toIso(row.updated_at)
  };
}

function toDateOnly(value) {
  if (value instanceof Date) return value.toISOString().slice(0, 10);
  return String(value).slice(0, 10);
}

function toIso(value) {
  return value instanceof Date ? value.toISOString() : new Date(value).toISOString();
}
