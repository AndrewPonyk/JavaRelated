<script setup lang="ts">
/** Search page: wires the Pinia store to the presentational components. */
import { storeToRefs } from "pinia";

import FacetPanel from "@/components/FacetPanel.vue";
import SearchBar from "@/components/SearchBar.vue";
import SearchResults from "@/components/SearchResults.vue";
import { useSearchStore } from "@/stores/search";
import type { ProductHit, SortOption } from "@/types/search";

const store = useSearchStore();
const { query, sort, hits, facets, meta, loading, error, hasSearched, priceMin, priceMax } =
  storeToRefs(store);

function onSortChange(event: Event): void {
  store.setSort((event.target as HTMLSelectElement).value as SortOption);
}

function onSelect(hit: ProductHit, position: number): void {
  store.recordClick(hit, position);
  // A product detail page would navigate here; click feedback already captured.
}
</script>

<template>
  <div class="search-view">
    <div class="toolbar">
      <SearchBar
        v-model="query"
        @submit="store.runSearch(1)"
      />
      <label class="sort">
        Sort by
        <select
          :value="sort"
          @change="onSortChange"
        >
          <option value="relevance">Relevance</option>
          <option value="price_asc">Price: low to high</option>
          <option value="price_desc">Price: high to low</option>
          <option value="newest">Newest</option>
        </select>
      </label>
    </div>

    <div class="layout">
      <FacetPanel
        v-if="facets.length"
        :facets="facets"
        :price-min="priceMin"
        :price-max="priceMax"
        @toggle="store.toggleFacet"
        @clear="store.clearFilters"
        @apply-price="store.setPriceRange"
      />
      <SearchResults
        :hits="hits"
        :meta="meta"
        :loading="loading"
        :error="error"
        :has-searched="hasSearched"
        @retry="store.runSearch(store.page)"
        @paginate="store.runSearch($event)"
        @select="onSelect"
      />
    </div>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  flex-wrap: wrap;
}
.toolbar > :first-child {
  flex: 1;
}
.sort {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: #475569;
  font-size: 0.9rem;
}
.sort select {
  padding: 0.45rem 0.6rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.5rem;
  background: #fff;
}
.layout {
  display: flex;
  gap: 2rem;
  margin-top: 1.5rem;
  align-items: flex-start;
}
.layout > :last-child {
  flex: 1;
}

/* Responsive: facets stack above results on narrow screens. */
@media (max-width: 768px) {
  .layout {
    flex-direction: column;
    gap: 1rem;
  }
  .layout > * {
    width: 100%;
  }
}
</style>
