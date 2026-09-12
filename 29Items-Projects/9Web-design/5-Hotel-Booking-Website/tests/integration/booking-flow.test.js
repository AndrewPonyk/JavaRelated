const { createTestDatabase } = require("../helpers/testDb");
const availabilityService = require("../../netlify/functions/_lib/availabilityService");
const bookingService = require("../../netlify/functions/_lib/bookingService");
const hotelService = require("../../netlify/functions/_lib/hotelService");
const roomService = require("../../netlify/functions/_lib/roomService");

let database;

beforeEach(async () => {
  database = await createTestDatabase();
});

afterEach(async () => {
  await database?.close();
});

describe("booking integration flow", () => {
  it("searches availability and creates a persisted booking", async () => {
    const rooms = await availabilityService.findAvailableRooms(
      {
        checkIn: "2026-07-01",
        checkOut: "2026-07-04",
        guests: 2,
        roomType: "standard",
        city: "New York",
      },
      database,
    );

    expect(rooms).toHaveLength(1);
    expect(rooms[0].totalPriceCents).toBe(60480);

    const booking = await bookingService.createBooking(
      {
        checkIn: "2026-07-01",
        checkOut: "2026-07-04",
        guests: 2,
        roomId: rooms[0].id,
        guestName: "Alex Morgan",
        guestEmail: "alex@example.com",
      },
      database,
    );

    expect(booking.confirmationCode).toMatch(/^HB-/);
    expect(booking.status).toBe("confirmed");
    expect(booking.totalPriceCents).toBe(60480);

    await expect(
      bookingService.createBooking(
        {
          checkIn: "2026-07-02",
          checkOut: "2026-07-03",
          guests: 2,
          roomId: rooms[0].id,
          guestName: "Taylor Reed",
          guestEmail: "taylor@example.com",
        },
        database,
      ),
    ).rejects.toMatchObject({ statusCode: 409 });
  });

  it("supports CRUD for hotels and rooms", async () => {
    const hotel = await hotelService.createHotel(
      {
        name: "Test Hotel",
        slug: "test-hotel",
        city: "Boston",
        address: "1 Test Street",
        description: "Integration hotel",
        latitude: 42.36,
        longitude: -71.05,
        amenities: ["Wi-Fi"],
      },
      database,
    );

    const room = await roomService.createRoom(
      {
        hotelId: hotel.id,
        roomType: "suite",
        roomNumber: "900",
        capacity: 4,
        basePriceCents: 45000,
        amenities: ["Harbor view"],
      },
      database,
    );

    expect(room.hotelName).toBe("Test Hotel");

    const updated = await roomService.updateRoom(room.id, { status: "maintenance" }, database);
    expect(updated.status).toBe("maintenance");

    await roomService.deleteRoom(room.id, database);
    await hotelService.deleteHotel(hotel.id, database);
  });
});
