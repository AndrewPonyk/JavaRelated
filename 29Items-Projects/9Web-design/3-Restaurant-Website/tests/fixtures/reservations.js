export function validReservation(overrides = {}) {
  return {
    name: 'Ada Lovelace',
    email: 'ada@example.com',
    date: futureDate(),
    partySize: 4,
    notes: 'Window table if available',
    ...overrides
  };
}

export function futureDate(days = 2) {
  const date = new Date();
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
}
