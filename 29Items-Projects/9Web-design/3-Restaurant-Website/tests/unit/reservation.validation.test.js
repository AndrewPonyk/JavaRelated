import { describe, expect, it } from 'vitest';
import { validateReservationInput, validateReservationUpdate } from '../../src/shared/validation/reservation.js';
import { futureDate, validReservation } from '../fixtures/reservations.js';

describe('validateReservationInput', () => {
  it('accepts a valid reservation request', () => {
    const result = validateReservationInput({
      name: 'Ada Lovelace',
      email: 'ada@example.com',
      date: futureDate(1),
      partySize: '4',
      notes: 'Window table if available'
    });

    expect(result.ok).toBe(true);
    expect(result.data.partySize).toBe(4);
  });

  it('rejects invalid customer input', () => {
    const result = validateReservationInput({
      name: '',
      email: 'not-email',
      date: '2020-01-01',
      partySize: '30'
    });

    expect(result.ok).toBe(false);
    expect(result.errors.length).toBeGreaterThan(0);
  });

  it('accepts valid partial updates', () => {
    const result = validateReservationUpdate({ status: 'confirmed', notes: 'Called guest.' });

    expect(result.ok).toBe(true);
    expect(result.data.status).toBe('confirmed');
  });

  it('rejects empty updates', () => {
    const result = validateReservationUpdate({});

    expect(result.ok).toBe(false);
    expect(result.errors[0].field).toBe('reservation');
  });

  it('rejects invalid partial update fields', () => {
    const result = validateReservationUpdate({
      email: 'bad-email',
      date: '2020-01-01',
      partySize: 0,
      notes: 'x'.repeat(1001),
      status: 'archived'
    });

    expect(result.ok).toBe(false);
    expect(result.errors.map((error) => error.field)).toEqual([
      'email',
      'date',
      'partySize',
      'notes',
      'status'
    ]);
  });

  it('normalizes email and default status', () => {
    const result = validateReservationInput(validReservation({ email: 'ADA@EXAMPLE.COM' }));

    expect(result.ok).toBe(true);
    expect(result.data.email).toBe('ada@example.com');
    expect(result.data.status).toBe('requested');
  });
});
