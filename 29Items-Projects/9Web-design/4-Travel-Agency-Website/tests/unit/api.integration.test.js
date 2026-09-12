const request = require("supertest");
const { createApp } = require("../../src/app");
const { configureTestDatabase } = require("../../src/api/db/test-database");

let app;

beforeEach(async () => {
  await configureTestDatabase();
  app = createApp();
});

describe("tour API", () => {
  test("lists seeded tours with filters", async () => {
    const response = await request(app).get("/api/tours").query({ region: "europe", maxDuration: 8, limit: 1 });

    expect(response.status).toBe(200);
    expect(response.body.meta).toEqual(expect.objectContaining({ count: 1, limit: 1, offset: 0 }));
    expect(response.body.data).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ slug: "amalfi-rail-coast", region: "europe", durationDays: 8 })
      ])
    );
  });

  test("creates, updates, reads, and deletes a tour", async () => {
    const createResponse = await request(app)
      .post("/api/tours")
      .send({
        slug: "andes-food-route",
        title: "Andes Food Route",
        region: "americas",
        regionLabel: "Americas",
        durationDays: 7,
        price: 2800,
        difficulty: "moderate",
        featured: false,
        summary: "A food-focused route through markets, farms, and mountain towns.",
        image: "https://example.com/andes.jpg"
      });

    expect(createResponse.status).toBe(201);
    const id = createResponse.body.data.id;

    const updateResponse = await request(app).patch(`/api/tours/${id}`).send({ price: 3000, featured: true });
    expect(updateResponse.status).toBe(200);
    expect(updateResponse.body.data.price).toBe(3000);
    expect(updateResponse.body.data.featured).toBe(true);

    const readResponse = await request(app).get(`/api/tours/${id}`);
    expect(readResponse.status).toBe(200);
    expect(readResponse.body.data.slug).toBe("andes-food-route");

    const deleteResponse = await request(app).delete(`/api/tours/${id}`).send({});
    expect(deleteResponse.status).toBe(204);

    const missingResponse = await request(app).get(`/api/tours/${id}`);
    expect(missingResponse.status).toBe(404);
  });

  test("rejects invalid tour input", async () => {
    const response = await request(app).post("/api/tours").send({ slug: "Bad Slug" });

    expect(response.status).toBe(400);
    expect(response.body.error.code).toBe("VALIDATION_ERROR");
  });

  test("rejects invalid JSON with a JSON error response", async () => {
    const response = await request(app).post("/api/tours").set("Content-Type", "application/json").send("{bad json");

    expect(response.status).toBe(400);
    expect(response.body.error.code).toBe("BAD_JSON");
  });
});

describe("destination API", () => {
  test("creates, updates, reads, filters, and deletes a destination", async () => {
    const createResponse = await request(app)
      .post("/api/destinations")
      .send({
        slug: "lisbon",
        name: "Lisbon",
        region: "Europe",
        latitude: 38.7223,
        longitude: -9.1393,
        description: "Food, tiles, viewpoints, and Atlantic day trips.",
        image: "https://example.com/lisbon.jpg"
      });

    expect(createResponse.status).toBe(201);
    const id = createResponse.body.data.id;

    const listResponse = await request(app).get("/api/destinations").query({ region: "Europe" });
    expect(listResponse.body.data.map((destination) => destination.slug)).toContain("lisbon");

    const updateResponse = await request(app).patch(`/api/destinations/${id}`).send({ description: "Updated Lisbon route." });
    expect(updateResponse.status).toBe(200);
    expect(updateResponse.body.data.description).toBe("Updated Lisbon route.");

    const readResponse = await request(app).get(`/api/destinations/${id}`);
    expect(readResponse.status).toBe(200);

    const deleteResponse = await request(app).delete(`/api/destinations/${id}`).send({});
    expect(deleteResponse.status).toBe(204);
  });

  test("rejects invalid coordinates", async () => {
    const response = await request(app).post("/api/destinations").send({
      slug: "invalid-place",
      name: "Invalid Place",
      region: "Nowhere",
      latitude: 120,
      longitude: 0,
      description: "Invalid coordinate example."
    });

    expect(response.status).toBe(400);
    expect(response.body.error.details).toEqual(expect.arrayContaining([expect.objectContaining({ field: "latitude" })]));
  });
});

describe("inquiry API", () => {
  test("creates, updates, lists, reads, and deletes an inquiry", async () => {
    const toursResponse = await request(app).get("/api/tours").query({ featured: "true" });
    const tourId = toursResponse.body.data[0].id;

    const createResponse = await request(app)
      .post("/api/inquiries")
      .send({
        tourId,
        name: "Taylor Client",
        email: "taylor@example.com",
        destination: "Kyoto",
        budget: "3000-6000",
        message: "We want a private culture-focused itinerary next spring.",
        consent: true
      });

    expect(createResponse.status).toBe(201);
    const id = createResponse.body.data.id;

    const updateResponse = await request(app).patch(`/api/inquiries/${id}`).send({ status: "contacted" });
    expect(updateResponse.status).toBe(200);
    expect(updateResponse.body.data.status).toBe("contacted");

    const listResponse = await request(app).get("/api/inquiries").query({ status: "contacted" });
    expect(listResponse.body.data).toHaveLength(1);

    const readResponse = await request(app).get(`/api/inquiries/${id}`);
    expect(readResponse.status).toBe(200);

    const deleteResponse = await request(app).delete(`/api/inquiries/${id}`).send({});
    expect(deleteResponse.status).toBe(204);
  });

  test("returns validation errors and method errors consistently", async () => {
    const invalidResponse = await request(app).post("/api/inquiries").send({
      name: "",
      email: "not-an-email",
      destination: "",
      message: "short",
      consent: false
    });

    expect(invalidResponse.status).toBe(400);
    expect(invalidResponse.body.error.code).toBe("VALIDATION_ERROR");

    const methodResponse = await request(app).put("/api/inquiries").send({});
    expect(methodResponse.status).toBe(405);
  });
});

describe("health check", () => {
  test("returns database health", async () => {
    const response = await request(app).get("/health");

    expect(response.status).toBe(200);
    expect(response.body).toEqual(expect.objectContaining({ status: "ok", database: "ok" }));
  });
});
