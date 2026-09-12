import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import FacetPanel from "@/components/FacetPanel.vue";
import type { FacetGroup } from "@/types/search";

const FACETS: FacetGroup[] = [
  {
    name: "brand",
    label: "Brand",
    values: [
      { value: "sony", count: 3, selected: true },
      { value: "lg", count: 2, selected: false },
    ],
  },
  {
    name: "price",
    label: "Price",
    values: [{ value: "100-250", count: 4, selected: false }],
  },
];

function factory() {
  return mount(FacetPanel, {
    props: { facets: FACETS, priceMin: null, priceMax: null },
  });
}

describe("FacetPanel", () => {
  it("renders groups with counts and selection state", () => {
    const wrapper = factory();
    expect(wrapper.text()).toContain("Brand");
    expect(wrapper.text()).toContain("sony");
    const checkboxes = wrapper.findAll('input[type="checkbox"]');
    expect((checkboxes[0].element as HTMLInputElement).checked).toBe(true);
    expect((checkboxes[1].element as HTMLInputElement).checked).toBe(false);
  });

  it("emits toggle on checkbox change", async () => {
    const wrapper = factory();
    await wrapper.findAll('input[type="checkbox"]')[1].setValue(true);
    expect(wrapper.emitted("toggle")?.[0]).toEqual(["brand", "lg"]);
  });

  it("emits clear from the clear-all button", async () => {
    const wrapper = factory();
    await wrapper.find("button.clear").trigger("click");
    expect(wrapper.emitted("clear")).toHaveLength(1);
  });

  it("validates the price form: min must not exceed max", async () => {
    const wrapper = factory();
    const [min, max] = wrapper.findAll(".price-form input");
    await min.setValue("500");
    await max.setValue("100");
    await wrapper.find(".price-form").trigger("submit");

    expect(wrapper.find(".price-error").text()).toContain("Min must be ≤ max");
    expect(wrapper.emitted("apply-price")).toBeUndefined();
  });

  it("emits apply-price with parsed numbers (empty means open-ended)", async () => {
    const wrapper = factory();
    const [min] = wrapper.findAll(".price-form input");
    await min.setValue("100");
    await wrapper.find(".price-form").trigger("submit");

    expect(wrapper.emitted("apply-price")?.[0]).toEqual([100, null]);
  });
});
