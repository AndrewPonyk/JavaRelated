const DEFAULT_LIMIT = 50;
const MAX_LIMIT = 100;

function getPagination(filters = {}) {
  const requestedLimit = Number(filters.limit);
  const requestedOffset = Number(filters.offset);
  const limit =
    Number.isInteger(requestedLimit) && requestedLimit > 0
      ? Math.min(requestedLimit, MAX_LIMIT)
      : DEFAULT_LIMIT;
  const offset = Number.isInteger(requestedOffset) && requestedOffset >= 0 ? requestedOffset : 0;

  return { limit, offset };
}

function appendPagination(values, filters = {}) {
  const { limit, offset } = getPagination(filters);
  values.push(limit, offset);

  return `LIMIT $${values.length - 1} OFFSET $${values.length}`;
}

module.exports = {
  DEFAULT_LIMIT,
  MAX_LIMIT,
  appendPagination,
  getPagination,
};
