const { requireStaff } = require("../../netlify/functions/_lib/auth");
const { parseJsonBody } = require("../../netlify/functions/_lib/response");
const {
  validateAvailabilityPayload,
  validateBookingPayload,
  validateGuestPayload,
  validateHotelPayload,
  validateRoomPayload,
  validateStaffUserPayload,
  validateStatusPayload,
} = require("../../netlify/functions/_lib/validation");

describe("validation negative paths", () => {
  it("rejects malformed dates, capacity, status, and email values", () => {
    expect(validateAvailabilityPayload({ checkIn: "bad", checkOut: "2026-01-02", guests: 1 }).valid).toBe(
      false,
    );
    expect(validateBookingPayload({ checkIn: "2026-01-01", checkOut: "2026-01-02", guests: 0 }).valid).toBe(
      false,
    );
    expect(validateRoomPayload({ roomType: "villa" }).valid).toBe(false);
    expect(validateRoomPayload({ capacity: 20 }, true).valid).toBe(false);
    expect(validateRoomPayload({ basePriceCents: -1 }, true).valid).toBe(false);
    expect(validateGuestPayload({ email: "bad" }, true).valid).toBe(false);
    expect(validateStatusPayload({ status: "unknown" }).valid).toBe(false);
  });

  it("rejects invalid hotel and staff payloads", () => {
    expect(validateHotelPayload({ name: "Bad", slug: "Bad Slug" }, true).valid).toBe(false);
    expect(validateHotelPayload({ latitude: "north" }, true).valid).toBe(false);
    expect(validateHotelPayload({ longitude: "west" }, true).valid).toBe(false);
    expect(validateStaffUserPayload({ email: "bad", displayName: "Desk" }).valid).toBe(false);
    expect(validateStaffUserPayload({ email: "desk@example.com", role: "owner" }, true).valid).toBe(false);
  });
});

describe("auth and response helpers", () => {
  const previousToken = process.env.STAFF_API_TOKEN;

  afterEach(() => {
    process.env.STAFF_API_TOKEN = previousToken;
  });

  it("rejects missing token configuration and insufficient role", () => {
    delete process.env.STAFF_API_TOKEN;
    expect(() => requireStaff({ headers: {} })).toThrow(/not configured/);

    process.env.STAFF_API_TOKEN = "secret";
    expect(() =>
      requireStaff(
        {
          headers: {
            "x-staff-token": "secret",
            "x-staff-role": "readonly",
          },
        },
        "manager",
      ),
    ).toThrow(/cannot perform/);
  });

  it("throws a validation error for invalid JSON", () => {
    expect(() => parseJsonBody({ body: "{" })).toThrow(/valid JSON/);
  });
});
