const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

function isIsoDate(value) {
  if (typeof value !== "string" || !ISO_DATE_PATTERN.test(value)) {
    return false;
  }

  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

function calculateNights(checkIn, checkOut) {
  const start = new Date(`${checkIn}T00:00:00Z`);
  const end = new Date(`${checkOut}T00:00:00Z`);
  const diff = end.getTime() - start.getTime();
  return Math.ceil(diff / 86_400_000);
}

function assertDateRange(checkIn, checkOut) {
  if (!isIsoDate(checkIn) || !isIsoDate(checkOut)) {
    return "Check-in and check-out must be valid ISO dates.";
  }

  if (checkOut <= checkIn) {
    return "Check-out must be after check-in.";
  }

  return null;
}

module.exports = {
  assertDateRange,
  calculateNights,
  isIsoDate,
};
