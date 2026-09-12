const { filterTours, sortTours } = require("../../src/assets/js/filters");

const tours = [
  { title: "A", region: "europe", price: 3000, durationDays: 8, featured: false },
  { title: "B", region: "asia", price: 2000, durationDays: 6, featured: true },
  { title: "C", region: "europe", price: 5000, durationDays: 12, featured: true }
];

describe("tour filters", () => {
  test("filters by region, price, and duration", () => {
    const result = filterTours(tours, { region: "europe", maxPrice: 3500, maxDuration: 10 });

    expect(result).toHaveLength(1);
    expect(result[0].title).toBe("A");
  });

  test("filters by difficulty", () => {
    const result = filterTours(
      [
        { title: "A", region: "europe", price: 3000, durationDays: 8, difficulty: "easy" },
        { title: "B", region: "europe", price: 3000, durationDays: 8, difficulty: "active" }
      ],
      { difficulty: "active" }
    );

    expect(result.map((tour) => tour.title)).toEqual(["B"]);
  });

  test("sorts by price ascending", () => {
    const result = sortTours(tours, "price-asc");

    expect(result.map((tour) => tour.title)).toEqual(["B", "A", "C"]);
  });
});
