function validateDestinationPayload(payload, options = {}) {
  const errors = [];
  const value = {
    slug: clean(payload.slug),
    name: clean(payload.name),
    region: clean(payload.region),
    latitude: numberOrUndefined(payload.latitude),
    longitude: numberOrUndefined(payload.longitude),
    description: clean(payload.description),
    image: clean(payload.image)
  };

  requireString(errors, value.slug, "slug", options.partial);
  requireString(errors, value.name, "name", options.partial);
  requireString(errors, value.region, "region", options.partial);
  requireString(errors, value.description, "description", options.partial);
  validateMaxLength(errors, value.slug, "slug", 120);
  validateMaxLength(errors, value.name, "name", 160);
  validateMaxLength(errors, value.region, "region", 80);
  validateMaxLength(errors, value.description, "description", 500);

  if (value.slug && !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(value.slug)) {
    errors.push({ field: "slug", message: "Slug must use lowercase letters, numbers, and hyphens." });
  }

  if ((value.latitude === undefined && !options.partial) || !isBoundedNumber(value.latitude, -90, 90)) {
    errors.push({ field: "latitude", message: "Latitude must be between -90 and 90." });
  }

  if ((value.longitude === undefined && !options.partial) || !isBoundedNumber(value.longitude, -180, 180)) {
    errors.push({ field: "longitude", message: "Longitude must be between -180 and 180." });
  }

  if (value.image && !isHttpUrl(value.image)) {
    errors.push({ field: "image", message: "Image must be a valid HTTP or HTTPS URL." });
  }

  return { valid: errors.length === 0, errors, value: stripUndefined(value, options.partial) };
}

function clean(value) {
  return typeof value === "string" ? value.trim() : undefined;
}

function numberOrUndefined(value) {
  return value === undefined || value === "" ? undefined : Number(value);
}

function requireString(errors, value, field, partial) {
  if (!partial && !value) {
    errors.push({ field, message: `${field} is required.` });
  }
}

function validateMaxLength(errors, value, field, maxLength) {
  if (value && value.length > maxLength) {
    errors.push({ field, message: `${field} must be ${maxLength} characters or fewer.` });
  }
}

function isBoundedNumber(value, min, max) {
  return value === undefined || (Number.isFinite(value) && value >= min && value <= max);
}

function isHttpUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch (_error) {
    return false;
  }
}

function stripUndefined(value, partial) {
  if (!partial) {
    return value;
  }

  return Object.fromEntries(Object.entries(value).filter(([, entry]) => entry !== undefined));
}

module.exports = { validateDestinationPayload };
