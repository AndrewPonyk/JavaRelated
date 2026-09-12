const REGIONS = ["europe", "asia", "americas", "africa"];
const DIFFICULTIES = ["easy", "moderate", "active"];

function validateTourPayload(payload, options = {}) {
  const errors = [];
  const value = {
    slug: clean(payload.slug),
    title: clean(payload.title),
    region: clean(payload.region),
    regionLabel: clean(payload.regionLabel),
    durationDays: numberOrUndefined(payload.durationDays),
    price: numberOrUndefined(payload.price),
    difficulty: clean(payload.difficulty),
    featured: payload.featured === undefined ? false : Boolean(payload.featured),
    summary: clean(payload.summary),
    image: clean(payload.image)
  };

  validateRequiredString(errors, value.slug, "slug", options.partial);
  validateRequiredString(errors, value.title, "title", options.partial);
  validateRequiredString(errors, value.region, "region", options.partial);
  validateRequiredString(errors, value.regionLabel, "regionLabel", options.partial);
  validateRequiredString(errors, value.difficulty, "difficulty", options.partial);
  validateRequiredString(errors, value.summary, "summary", options.partial);
  validateRequiredString(errors, value.image, "image", options.partial);
  validateMaxLength(errors, value.slug, "slug", 120);
  validateMaxLength(errors, value.title, "title", 160);
  validateMaxLength(errors, value.regionLabel, "regionLabel", 80);
  validateMaxLength(errors, value.summary, "summary", 500);

  if (value.slug && !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(value.slug)) {
    errors.push({ field: "slug", message: "Slug must use lowercase letters, numbers, and hyphens." });
  }

  if (value.region && !REGIONS.includes(value.region)) {
    errors.push({ field: "region", message: `Region must be one of: ${REGIONS.join(", ")}.` });
  }

  if (value.difficulty && !DIFFICULTIES.includes(value.difficulty)) {
    errors.push({ field: "difficulty", message: `Difficulty must be one of: ${DIFFICULTIES.join(", ")}.` });
  }

  if ((value.durationDays === undefined && !options.partial) || !isPositiveInteger(value.durationDays)) {
    errors.push({ field: "durationDays", message: "Duration must be greater than zero." });
  }

  if ((value.price === undefined && !options.partial) || !isPositiveNumber(value.price)) {
    errors.push({ field: "price", message: "Price must be greater than zero." });
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

function validateRequiredString(errors, value, field, partial) {
  if (!partial && !value) {
    errors.push({ field, message: `${field} is required.` });
  }
}

function validateMaxLength(errors, value, field, maxLength) {
  if (value && value.length > maxLength) {
    errors.push({ field, message: `${field} must be ${maxLength} characters or fewer.` });
  }
}

function isPositiveInteger(value) {
  return value === undefined || (Number.isInteger(value) && value > 0 && value <= 365);
}

function isPositiveNumber(value) {
  return value === undefined || (Number.isFinite(value) && value > 0 && value <= 1000000);
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

module.exports = { DIFFICULTIES, REGIONS, validateTourPayload };
