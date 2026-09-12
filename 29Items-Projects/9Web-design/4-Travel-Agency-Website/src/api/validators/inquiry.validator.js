function validateInquiryPayload(payload, options = {}) {
  const errors = [];
  const value = {
    tourId: numberOrUndefined(payload.tourId),
    name: clean(payload.name),
    email: clean(payload.email),
    destination: clean(payload.destination),
    budget: clean(payload.budget),
    message: clean(payload.message),
    consent: payload.consent === undefined ? undefined : Boolean(payload.consent),
    status: clean(payload.status)
  };

  if (!options.partial && !value.name) {
    errors.push({ field: "name", message: "Name is required." });
  }
  validateMaxLength(errors, value.name, "name", 160);

  if ((!options.partial || value.email) && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.email || "")) {
    errors.push({ field: "email", message: "A valid email is required." });
  }
  validateMaxLength(errors, value.email, "email", 254);

  if (!options.partial && !value.destination) {
    errors.push({ field: "destination", message: "Destination is required." });
  }
  validateMaxLength(errors, value.destination, "destination", 160);
  validateMaxLength(errors, value.budget, "budget", 80);

  if ((!options.partial || value.message) && (!value.message || value.message.length < 10)) {
    errors.push({ field: "message", message: "Message must be at least 10 characters." });
  }
  validateMaxLength(errors, value.message, "message", 2000);

  if (!options.partial && !value.consent) {
    errors.push({ field: "consent", message: "Consent is required." });
  }

  if (value.tourId !== undefined && (!Number.isInteger(value.tourId) || value.tourId <= 0)) {
    errors.push({ field: "tourId", message: "Tour id must be a positive integer." });
  }

  return {
    valid: errors.length === 0,
    errors,
    value: stripUndefined(value, options.partial)
  };
}

function clean(value) {
  return typeof value === "string" ? value.trim() : undefined;
}

function numberOrUndefined(value) {
  return value === undefined || value === "" ? undefined : Number(value);
}

function validateMaxLength(errors, value, field, maxLength) {
  if (value && value.length > maxLength) {
    errors.push({ field, message: `${field} must be ${maxLength} characters or fewer.` });
  }
}

function stripUndefined(value, partial) {
  if (!partial) {
    return value;
  }

  return Object.fromEntries(Object.entries(value).filter(([, entry]) => entry !== undefined));
}

module.exports = { validateInquiryPayload };
