const { forbidden, unauthorized } = require("./errors");

const roleRank = {
  readonly: 1,
  receptionist: 2,
  manager: 3,
};

function getHeader(event, name) {
  const headers = event.headers || {};
  const lowerName = name.toLowerCase();
  const key = Object.keys(headers).find((candidate) => candidate.toLowerCase() === lowerName);
  return key ? headers[key] : undefined;
}

function requireStaff(event, minimumRole = "receptionist") {
  const configuredToken = process.env.STAFF_API_TOKEN;

  if (!configuredToken) {
    throw unauthorized("STAFF_API_TOKEN is not configured.");
  }

  const token = getHeader(event, "x-staff-token");
  const role = getHeader(event, "x-staff-role") || "manager";

  if (token !== configuredToken) {
    throw unauthorized();
  }

  if ((roleRank[role] || 0) < roleRank[minimumRole]) {
    throw forbidden();
  }

  return { role };
}

module.exports = {
  requireStaff,
};
