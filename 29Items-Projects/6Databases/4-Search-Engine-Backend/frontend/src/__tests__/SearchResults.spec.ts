import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import SearchResults from "@/components/SearchResults.vue";
import type { PageMeta, ProductHit } from "@/types/search";

const HIT: ProductHit = {
  id: "p1",
  sku: "SKU-1",
  name: "Sony Bravia 55",
  brand: "sony",
  price: 999.99,
  category_slug: "tvs",
  in_stock: true,
  score: 12,
};

const META: PageMeta = { page: 1, size: 20, total: 45, pages: 3 };

function factory(props: Partial<InstanceType<typeof SearchResults>["$props"]> = {}) {
  return mount(SearchResults, {
    props: {
      hits: [],
      meta: null,
      loading: false,
      error: null,
      hasSearched: false,
      ...props,
    },
  });
}

describe("SearchResults", () => {
  it("shows skeletons while loading", () => {
    const wrapper = factory({ loading: true });
    expect(wrapper.findAll(".skeleton").length).toBeGreaterThan(0);
  });

  it("shows the error state and emits retry", async () => {
    const wrapper = factory({ error: "Search is temporarily unavailable." });
    expect(wrapper.find('[role="alert"]').text()).toContain("temporarily unavailable");
    await wrapper.find(".state.error button").trigger("click");
    expect(wrapper.emitted("retry")).toHaveLength(1);
  });

  it("shows the empty state after a search with no hits", () => {
    const wrapper = factory({ hasSearched: true });
    expect(wrapper.text()).toContain("No products found");
  });

  it("renders hits and emits select with the hit position", async () => {
    const wrapper = factory({ hits: [HIT], meta: { ...META, total: 1, pages: 1 } });
    expect(wrapper.text()).toContain("Sony Bravia 55");
    await wrapper.find(".card").trigger("click");
    expect(wrapper.emitted("select")?.[0]).toEqual([HIT, 0]);
  });

  it("paginates forward and disables the back button on page 1", async () => {
    const wrapper = factory({ hits: [HIT], meta: META });
    const [prev, next] = wrapper.findAll(".pagination button");
    expect(prev.attributes("disabled")).toBeDefined();
    await next.trigger("click");
    expect(wrapper.emitted("paginate")?.[0]).toEqual([2]);
  });
});
