const { calculateNights } = require("../../netlify/functions/_lib/dateUtils");
const { calculatePrice } = require("../../netlify/functions/_lib/availabilityService");
const {
  validateAvailabilityPayload,
  validateBookingPayload,
  validateRoomPayload,
} = require("../../netlify/functions/_lib/validation");

describe("booking business rules", () => {
  it("calculates date-only booking nights", () => {
    expect(calculateNights("2026-06-01", "2026-06-04")).toBe(3);
  });

  it("calculates subtotal, tax, and total from server-side room price", () => {
    expect(calculatePrice(10000, 2)).toEqual({
      nightlySubtotalCents: 20000,
      taxesCents: 2400,
      totalPriceCents: 22400,
    });
  });

  it("rejects invalid availability dates", () => {
    expect(
      validateAvailabilityPayload({
        checkIn: "2026-06-04",
        checkOut: "2026-06-01",
        guests: 2,
      }),
    ).toEqual({ valid: false, message: "Check-out must be after check-in." });
  });

  it("requires booking guest identity and room selection criteria", () => {
    expect(
      validateBookingPayload({
        checkIn: "2026-06-01",
        checkOut: "2026-06-04",
        guests: 2,
        roomType: "standard",
        guestName: "Alex Morgan",
        guestEmail: "alex@example.com",
      }),
    ).toEqual({ valid: true });
  });

  it("validates room management payloads", () => {
    expect(
      validateRoomPayload({
        hotelId: "11111111-1111-4111-8111-111111111111",
        roomType: "suite",
        roomNumber: "501",
        capacity: 4,
        basePriceCents: 35000,
      }),
    ).toEqual({ valid: true });
  });
});
