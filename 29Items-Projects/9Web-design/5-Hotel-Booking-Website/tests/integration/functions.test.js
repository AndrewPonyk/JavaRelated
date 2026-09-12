const { createTestDatabase } = require("../helpers/testDb");
const availability = require("../../netlify/functions/availability");
const bookings = require("../../netlify/functions/bookings");
const guests = require("../../netlify/functions/guests");
const hotels = require("../../netlify/functions/hotels");
const rooms = require("../../netlify/functions/rooms");
const staffUsers = require("../../netlify/functions/staff-users");

let database;

function event(method, body, query = {}, headers = {}) {
  return {
    httpMethod: method,
    body: body ? JSON.stringify(body) : null,
    queryStringParameters: query,
    headers,
  };
}

beforeEach(async () => {
  process.env.STAFF_API_TOKEN = "test-token";
  database = await createTestDatabase();
});

afterEach(async () => {
  await database?.close();
});

describe("Netlify function endpoints", () => {
  const staffHeaders = { "x-staff-token": "test-token", "x-staff-role": "manager" };

  it("checks availability through the public endpoint", async () => {
    const response = await availability.handler(
      event("GET", null, {
        checkIn: "2026-08-01",
        checkOut: "2026-08-03",
        guests: "2",
        roomType: "standard",
      }),
    );

    expect(response.statusCode).toBe(200);
    expect(JSON.parse(response.body).data.length).toBeGreaterThan(0);
  });

  it("creates bookings through the public endpoint", async () => {
    const response = await bookings.handler(
      event("POST", {
        checkIn: "2026-08-01",
        checkOut: "2026-08-03",
        guests: 2,
        roomType: "standard",
        city: "New York",
        guestName: "Jamie Chen",
        guestEmail: "jamie@example.com",
      }),
    );

    expect(response.statusCode).toBe(201);
    const body = JSON.parse(response.body);
    expect(body.data.confirmationCode).toMatch(/^HB-/);
  });

  it("protects staff endpoints with a token", async () => {
    const denied = await bookings.handler(event("GET"));
    expect(denied.statusCode).toBe(401);

    const allowed = await bookings.handler(
      event("GET", null, {}, staffHeaders),
    );
    expect(allowed.statusCode).toBe(200);
  });

  it("updates room status through CRUD endpoint", async () => {
    const list = await rooms.handler(event("GET"));
    const firstRoom = JSON.parse(list.body).data[0];

    const updated = await rooms.handler(
      event(
        "PATCH",
        { status: "maintenance" },
        { id: firstRoom.id },
        staffHeaders,
      ),
    );

    expect(updated.statusCode).toBe(200);
    expect(JSON.parse(updated.body).data.status).toBe("maintenance");
  });

  it("performs hotel CRUD through staff endpoint", async () => {
    const created = await hotels.handler(
      event(
        "POST",
        {
          name: "Endpoint Hotel",
          slug: "endpoint-hotel",
          city: "Chicago",
          address: "9 Endpoint Ave",
          description: "Endpoint test",
          latitude: 41.88,
          longitude: -87.62,
          amenities: ["Wi-Fi"],
        },
        {},
        staffHeaders,
      ),
    );
    const hotel = JSON.parse(created.body).data;
    expect(created.statusCode).toBe(201);

    const updated = await hotels.handler(
      event("PATCH", { city: "Evanston" }, { id: hotel.id }, staffHeaders),
    );
    expect(JSON.parse(updated.body).data.city).toBe("Evanston");

    const listed = await hotels.handler(event("GET"));
    expect(JSON.parse(listed.body).data.length).toBeGreaterThan(0);

    const deleted = await hotels.handler(event("DELETE", null, { id: hotel.id }, staffHeaders));
    expect(deleted.statusCode).toBe(200);
  });

  it("performs guest CRUD through staff endpoint", async () => {
    const created = await guests.handler(
      event(
        "POST",
        { fullName: "Guest One", email: "guest-one@example.com", phone: "555-0101" },
        {},
        staffHeaders,
      ),
    );
    const guest = JSON.parse(created.body).data;
    expect(created.statusCode).toBe(201);

    const updated = await guests.handler(
      event("PATCH", { phone: "555-0102" }, { id: guest.id }, staffHeaders),
    );
    expect(JSON.parse(updated.body).data.phone).toBe("555-0102");

    const listed = await guests.handler(event("GET", null, {}, staffHeaders));
    expect(JSON.parse(listed.body).data.length).toBeGreaterThan(0);

    const deleted = await guests.handler(event("DELETE", null, { id: guest.id }, staffHeaders));
    expect(deleted.statusCode).toBe(200);
  });

  it("performs staff user CRUD through manager endpoint", async () => {
    const created = await staffUsers.handler(
      event(
        "POST",
        { email: "frontdesk@example.com", role: "receptionist", displayName: "Front Desk" },
        {},
        staffHeaders,
      ),
    );
    const staffUser = JSON.parse(created.body).data;
    expect(created.statusCode).toBe(201);

    const updated = await staffUsers.handler(
      event("PATCH", { role: "readonly" }, { id: staffUser.id }, staffHeaders),
    );
    expect(JSON.parse(updated.body).data.role).toBe("readonly");

    const listed = await staffUsers.handler(event("GET", null, {}, staffHeaders));
    expect(JSON.parse(listed.body).data.length).toBeGreaterThan(0);

    const deleted = await staffUsers.handler(event("DELETE", null, { id: staffUser.id }, staffHeaders));
    expect(deleted.statusCode).toBe(200);
  });
});
