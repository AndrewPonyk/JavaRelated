const { validateInquiryPayload } = require("../../src/api/validators/inquiry.validator");
const { validateTourPayload } = require("../../src/api/validators/tour.validator");
const { validateDestinationPayload } = require("../../src/api/validators/destination.validator");

describe("validators", () => {
  test("normalizes a valid inquiry", () => {
    const result = validateInquiryPayload({
      name: "  Alex  ",
      email: "alex@example.com",
      destination: "Amalfi",
      message: "Please help plan a family trip.",
      consent: true
    });

    expect(result.valid).toBe(true);
    expect(result.value.name).toBe("Alex");
  });

  test("rejects invalid tour metadata", () => {
    const result = validateTourPayload({
      slug: "bad slug",
      title: "Bad",
      region: "moon",
      regionLabel: "Moon",
      durationDays: 0,
      price: -1,
      difficulty: "extreme",
      summary: "Invalid",
      image: "javascript:alert(1)"
    });

    expect(result.valid).toBe(false);
    expect(result.errors.map((error) => error.field)).toEqual(
      expect.arrayContaining(["slug", "region", "durationDays", "price", "difficulty", "image"])
    );
  });

  test("rejects invalid inquiry ids and oversized text", () => {
    const result = validateInquiryPayload({
      tourId: -1,
      name: "A".repeat(161),
      email: "alex@example.com",
      destination: "Amalfi",
      message: "Please help plan a family trip.",
      consent: true
    });

    expect(result.valid).toBe(false);
    expect(result.errors.map((error) => error.field)).toEqual(expect.arrayContaining(["tourId", "name"]));
  });

  test("allows partial destination updates", () => {
    const result = validateDestinationPayload({ description: "Updated" }, { partial: true });

    expect(result.valid).toBe(true);
    expect(result.value).toEqual({ description: "Updated" });
  });
});
