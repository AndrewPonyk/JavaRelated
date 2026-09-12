const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
export const RESERVATION_STATUSES = ['requested', 'confirmed', 'declined', 'cancelled'];

export function validateReservationInput(input) {
  const errors = [];
  const data = {
    name: String(input.name || '').trim(),
    email: String(input.email || '').trim().toLowerCase(),
    date: String(input.date || '').trim(),
    partySize: Number(input.partySize),
    notes: String(input.notes || '').trim(),
    company: String(input.company || '').trim(),
    status: normalizeStatus(input.status || 'requested')
  };

  if (data.company) {
    errors.push({ field: 'company', message: 'Reservation could not be submitted.' });
  }

  if (data.name.length < 2) {
    errors.push({ field: 'name', message: 'Please enter your name.' });
  }

  if (!EMAIL_PATTERN.test(data.email)) {
    errors.push({ field: 'email', message: 'Please enter a valid email address.' });
  }

  if (!isFutureOrToday(data.date)) {
    errors.push({ field: 'date', message: 'Please choose today or a future date.' });
  }

  if (!Number.isInteger(data.partySize) || data.partySize < 1 || data.partySize > 12) {
    errors.push({ field: 'partySize', message: 'Party size must be between 1 and 12.' });
  }

  if (data.notes.length > 1000) {
    errors.push({ field: 'notes', message: 'Notes must be under 1000 characters.' });
  }

  if (!RESERVATION_STATUSES.includes(data.status)) {
    errors.push({ field: 'status', message: 'Reservation status is invalid.' });
  }

  return errors.length > 0 ? { ok: false, errors } : { ok: true, data };
}

export function validateReservationUpdate(input) {
  const errors = [];
  const data = {};

  if (Object.prototype.hasOwnProperty.call(input, 'name')) {
    data.name = String(input.name || '').trim();
    if (data.name.length < 2) {
      errors.push({ field: 'name', message: 'Please enter your name.' });
    }
  }

  if (Object.prototype.hasOwnProperty.call(input, 'email')) {
    data.email = String(input.email || '').trim().toLowerCase();
    if (!EMAIL_PATTERN.test(data.email)) {
      errors.push({ field: 'email', message: 'Please enter a valid email address.' });
    }
  }

  if (Object.prototype.hasOwnProperty.call(input, 'date')) {
    data.date = String(input.date || '').trim();
    if (!isFutureOrToday(data.date)) {
      errors.push({ field: 'date', message: 'Please choose today or a future date.' });
    }
  }

  if (Object.prototype.hasOwnProperty.call(input, 'partySize')) {
    data.partySize = Number(input.partySize);
    if (!Number.isInteger(data.partySize) || data.partySize < 1 || data.partySize > 12) {
      errors.push({ field: 'partySize', message: 'Party size must be between 1 and 12.' });
    }
  }

  if (Object.prototype.hasOwnProperty.call(input, 'notes')) {
    data.notes = String(input.notes || '').trim();
    if (data.notes.length > 1000) {
      errors.push({ field: 'notes', message: 'Notes must be under 1000 characters.' });
    }
  }

  if (Object.prototype.hasOwnProperty.call(input, 'status')) {
    data.status = normalizeStatus(input.status);
    if (!RESERVATION_STATUSES.includes(data.status)) {
      errors.push({ field: 'status', message: 'Reservation status is invalid.' });
    }
  }

  if (Object.keys(data).length === 0) {
    errors.push({ field: 'reservation', message: 'At least one reservation field is required.' });
  }

  return errors.length > 0 ? { ok: false, errors } : { ok: true, data };
}

function isFutureOrToday(value) {
  if (!value) return false;

  const selected = new Date(`${value}T00:00:00`);
  if (Number.isNaN(selected.getTime())) return false;

  const today = new Date();
  today.setHours(0, 0, 0, 0);

  return selected >= today;
}

function normalizeStatus(value) {
  return String(value || '').trim().toLowerCase();
}
