/**
 * Search page state machine. Owns query/filters/pagination, executes searches with
 * cancellation (AbortController) and a stale-response guard so out-of-order network
 * responses never overwrite newer results. Also emits interaction events (clicks).
 */

import { defineStore } from "pinia";
import { computed, ref, shallowRef } from "vue";

import { ApiError } from "@/api/client";
import { postClickEvent } from "@/api/events";
import { searchProducts } from "@/api/search";
import type { ProductHit, SearchResponse, SortOption } from "@/types/search";

const PAGE_SIZE = 20;

export const useSearchStore = defineStore("search", () => {
  const query = ref("");
  const filters = ref<Record<string, string[]>>({});
  const priceMin = ref<number | null>(null);
  const priceMax = ref<number | null>(null);
  const sort = ref<SortOption>("relevance");
  const page = ref(1);

  const response = shallowRef<SearchResponse | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);

  let controller: AbortController | null = null;

  const hits = computed(() => response.value?.hits ?? []);
  const facets = computed(() => response.value?.facets ?? []);
  const meta = computed(() => response.value?.meta ?? null);
  const hasSearched = computed(() => response.value !== null);

  async function runSearch(toPage = 1): Promise<void> {
    if (!query.value.trim()) {
      response.value = null;
      error.value = null;
      return;
    }

    controller?.abort();
    const mine = new AbortController();
    controller = mine;

    loading.value = true;
    error.value = null;
    page.value = toPage;

    try {
      response.value = await searchProducts(
        {
          q: query.value,
          category: filters.value.category,
          brand: filters.value.brand,
          price_min: priceMin.value ?? undefined,
          price_max: priceMax.value ?? undefined,
          sort: sort.value,
          page: toPage,
          size: PAGE_SIZE,
        },
        mine.signal,
      );
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError") return; // superseded
      error.value =
        e instanceof ApiError && e.status !== 500
          ? e.message
          : "Search is temporarily unavailable. Please try again.";
    } finally {
      if (controller === mine) loading.value = false; // ignore stale finishes
    }
  }

  function toggleFacet(group: string, value: string): void {
    const current = filters.value[group] ?? [];
    filters.value = {
      ...filters.value,
      [group]: current.includes(value)
        ? current.filter((v) => v !== value)
        : [...current, value],
    };
    void runSearch(1);
  }

  function setPriceRange(min: number | null, max: number | null): void {
    priceMin.value = min;
    priceMax.value = max;
    void runSearch(1);
  }

  function setSort(value: SortOption): void {
    sort.value = value;
    void runSearch(1);
  }

  function clearFilters(): void {
    filters.value = {};
    priceMin.value = null;
    priceMax.value = null;
    void runSearch(1);
  }

  function recordClick(hit: ProductHit, position: number): void {
    // Log against the query the results came from, not what's being typed now.
    const executedQuery = response.value?.query ?? query.value;
    if (!executedQuery) return;
    postClickEvent({ query: executedQuery, product_id: hit.id, position });
  }

  return {
    query,
    filters,
    priceMin,
    priceMax,
    sort,
    page,
    hits,
    facets,
    meta,
    loading,
    error,
    hasSearched,
    runSearch,
    toggleFacet,
    setPriceRange,
    setSort,
    clearFilters,
    recordClick,
  };
});
