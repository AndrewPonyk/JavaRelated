import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/api/client";
import { postClickEvent } from "@/api/events";
import { searchProducts } from "@/api/search";
import { useSearchStore } from "@/stores/search";
import type { SearchResponse } from "@/types/search";

vi.mock("@/api/search", () => ({ searchProducts: vi.fn() }));
vi.mock("@/api/events", () => ({ postClickEvent: vi.fn() }));

const mockedSearch = vi.mocked(searchProducts);
const mockedClick = vi.mocked(postClickEvent);

const RESPONSE: SearchResponse = {
  query: "tv",
  hits: [
    {
      id: "p1",
      sku: "SKU-1",
      name: "Sony TV",
      brand: "sony",
      price: 999,
      category_slug: "tvs",
      in_stock: true,
      score: 10,
    },
  ],
  facets: [
    {
      name: "brand",
      label: "Brand",
      values: [{ value: "sony", count: 1, selected: false }],
    },
  ],
  meta: { page: 1, size: 20, total: 1, pages: 1 },
  took_ms: 5,
};

describe("search store", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it("runs a search and exposes hits, facets and meta", async () => {
    mockedSearch.mockResolvedValueOnce(RESPONSE);
    const store = useSearchStore();
    store.query = "tv";

    await store.runSearch(1);

    expect(store.hits).toHaveLength(1);
    expect(store.facets[0].name).toBe("brand");
    expect(store.meta?.total).toBe(1);
    expect(store.loading).toBe(false);
    expect(store.error).toBeNull();
  });

  it("clears results without calling the API when the query is blank", async () => {
    const store = useSearchStore();
    store.query = "   ";
    await store.runSearch(1);
    expect(mockedSearch).not.toHaveBeenCalled();
    expect(store.hasSearched).toBe(false);
  });

  it("surfaces API problem details as a user-facing error", async () => {
    mockedSearch.mockRejectedValueOnce(new ApiError(422, "price_min must be <= price_max"));
    const store = useSearchStore();
    store.query = "tv";

    await store.runSearch(1);

    expect(store.error).toBe("price_min must be <= price_max");
    expect(store.hits).toHaveLength(0);
  });

  it("uses a generic message for 500s and outages", async () => {
    mockedSearch.mockRejectedValueOnce(new ApiError(500, "boom"));
    const store = useSearchStore();
    store.query = "tv";
    await store.runSearch(1);
    expect(store.error).toMatch(/temporarily unavailable/i);
  });

  it("ignores aborted (superseded) requests silently", async () => {
    mockedSearch.mockRejectedValueOnce(new DOMException("aborted", "AbortError"));
    const store = useSearchStore();
    store.query = "tv";
    await store.runSearch(1);
    expect(store.error).toBeNull();
  });

  it("toggleFacet adds then removes a selection and re-searches page 1", async () => {
    mockedSearch.mockResolvedValue(RESPONSE);
    const store = useSearchStore();
    store.query = "tv";

    store.toggleFacet("brand", "sony");
    await vi.waitFor(() => expect(mockedSearch).toHaveBeenCalledTimes(1));
    expect(mockedSearch.mock.calls[0][0].brand).toEqual(["sony"]);

    store.toggleFacet("brand", "sony");
    await vi.waitFor(() => expect(mockedSearch).toHaveBeenCalledTimes(2));
    expect(mockedSearch.mock.calls[1][0].brand).toEqual([]);
  });

  it("setPriceRange forwards min/max to the API", async () => {
    mockedSearch.mockResolvedValue(RESPONSE);
    const store = useSearchStore();
    store.query = "tv";

    store.setPriceRange(100, 500);
    await vi.waitFor(() => expect(mockedSearch).toHaveBeenCalled());

    const params = mockedSearch.mock.calls[0][0];
    expect(params.price_min).toBe(100);
    expect(params.price_max).toBe(500);
  });

  it("records clicks against the executed query, not the draft input", async () => {
    mockedSearch.mockResolvedValueOnce(RESPONSE);
    const store = useSearchStore();
    store.query = "tv";
    await store.runSearch(1);

    store.query = "half-typed new quer"; // user keeps typing
    store.recordClick(RESPONSE.hits[0], 0);

    expect(mockedClick).toHaveBeenCalledWith({ query: "tv", product_id: "p1", position: 0 });
  });
});
