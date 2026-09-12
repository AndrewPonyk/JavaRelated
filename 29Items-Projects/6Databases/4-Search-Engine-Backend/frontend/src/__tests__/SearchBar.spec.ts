import { mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { fetchSuggestions } from "@/api/search";
import SearchBar from "@/components/SearchBar.vue";

vi.mock("@/api/search", () => ({ fetchSuggestions: vi.fn() }));
const mockedSuggest = vi.mocked(fetchSuggestions);

async function typeAndWait(wrapper: ReturnType<typeof mount>, text: string): Promise<void> {
  const input = wrapper.find("input");
  await input.setValue(text);
  await vi.advanceTimersByTimeAsync(200); // past the 150 ms debounce
  await wrapper.vm.$nextTick();
}

describe("SearchBar", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers(); // discard pending debounce timers between tests
  });

  it("debounces input and shows fetched suggestions", async () => {
    mockedSuggest.mockResolvedValueOnce(["laptop", "laptop stand"]);
    const wrapper = mount(SearchBar);

    await typeAndWait(wrapper, "lapt");

    expect(mockedSuggest).toHaveBeenCalledTimes(1);
    const options = wrapper.findAll('[role="option"]');
    expect(options).toHaveLength(2);
    expect(options[0].text()).toBe("laptop");
  });

  it("does not query for prefixes shorter than 2 chars", async () => {
    const wrapper = mount(SearchBar);
    await typeAndWait(wrapper, "l");
    expect(mockedSuggest).not.toHaveBeenCalled();
  });

  it("navigates with arrow keys and picks with Enter", async () => {
    mockedSuggest.mockResolvedValueOnce(["laptop", "laptop stand"]);
    const wrapper = mount(SearchBar);
    await typeAndWait(wrapper, "lapt");

    const input = wrapper.find("input");
    await input.trigger("keydown.down");
    await input.trigger("keydown.down");
    await wrapper.find("form").trigger("submit");

    const updates = wrapper.emitted("update:modelValue") ?? [];
    expect(updates[updates.length - 1]).toEqual(["laptop stand"]);
    expect(wrapper.emitted("submit")).toHaveLength(1);
    expect(wrapper.find('[role="listbox"]').exists()).toBe(false); // closed after pick
  });

  it("submits the raw query when nothing is highlighted", async () => {
    const wrapper = mount(SearchBar);
    await wrapper.find("input").setValue("headphones");
    await wrapper.find("form").trigger("submit");
    expect(wrapper.emitted("submit")).toHaveLength(1);
  });

  it("closes the dropdown on Escape", async () => {
    mockedSuggest.mockResolvedValueOnce(["laptop"]);
    const wrapper = mount(SearchBar);
    await typeAndWait(wrapper, "lapt");
    expect(wrapper.find('[role="listbox"]').exists()).toBe(true);

    await wrapper.find("input").trigger("keydown.esc");
    expect(wrapper.find('[role="listbox"]').exists()).toBe(false);
  });

  it("fails silently when the suggest API errors", async () => {
    mockedSuggest.mockRejectedValueOnce(new Error("boom"));
    const wrapper = mount(SearchBar);
    await typeAndWait(wrapper, "lapt");
    expect(wrapper.find('[role="listbox"]').exists()).toBe(false);
  });
});
