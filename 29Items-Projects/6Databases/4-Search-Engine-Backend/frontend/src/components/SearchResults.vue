<script setup lang="ts">
/**
 * Results list with the three canonical remote-data states:
 * loading (skeleton) → error (retry) → data (grid + pagination) / empty.
 */
import type { PageMeta, ProductHit } from "@/types/search";

import ProductCard from "./ProductCard.vue";

defineProps<{
  hits: ProductHit[];
  meta: PageMeta | null;
  loading: boolean;
  error: string | null;
  hasSearched: boolean;
}>();

const emit = defineEmits<{
  retry: [];
  paginate: [page: number];
  select: [hit: ProductHit, position: number];
}>();
</script>

<template>
  <section
    class="results"
    aria-live="polite"
  >
    <!-- Loading -->
    <div
      v-if="loading"
      class="grid"
    >
      <div
        v-for="n in 6"
        :key="n"
        class="skeleton"
      />
    </div>

    <!-- Error -->
    <div
      v-else-if="error"
      class="state error"
      role="alert"
    >
      <p>{{ error }}</p>
      <button
        type="button"
        @click="emit('retry')"
      >
        Try again
      </button>
    </div>

    <!-- Empty -->
    <div
      v-else-if="hasSearched && hits.length === 0"
      class="state"
    >
      <p>No products found. Try different keywords or remove some filters.</p>
    </div>

    <!-- Data -->
    <template v-else-if="hits.length">
      <p class="summary">
        {{ meta?.total }} results
      </p>
      <div class="grid">
        <ProductCard
          v-for="(hit, index) in hits"
          :key="hit.id"
          :hit="hit"
          @select="emit('select', hit, index)"
        />
      </div>

      <nav
        v-if="meta && meta.pages > 1"
        class="pagination"
        aria-label="Pagination"
      >
        <button
          type="button"
          :disabled="meta.page <= 1"
          @click="emit('paginate', meta.page - 1)"
        >
          ← Previous
        </button>
        <span>Page {{ meta.page }} of {{ meta.pages }}</span>
        <button
          type="button"
          :disabled="meta.page >= meta.pages"
          @click="emit('paginate', meta.page + 1)"
        >
          Next →
        </button>
      </nav>
    </template>

    <!-- Initial -->
    <div
      v-else
      class="state"
    >
      <p>Start typing to search the catalog.</p>
    </div>
  </section>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr));
  gap: 1rem;
}
.skeleton {
  height: 9rem;
  border-radius: 0.75rem;
  background: linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%);
  background-size: 200% 100%;
  animation: shimmer 1.2s infinite;
}
@keyframes shimmer {
  to {
    background-position: -200% 0;
  }
}
.state {
  padding: 3rem 1rem;
  text-align: center;
  color: #64748b;
}
.state.error button {
  margin-top: 0.75rem;
  padding: 0.5rem 1.25rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.5rem;
  background: #fff;
  cursor: pointer;
}
.summary {
  color: #64748b;
  font-size: 0.9rem;
}
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  margin-top: 1.5rem;
}
.pagination button {
  padding: 0.4rem 0.9rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.5rem;
  background: #fff;
  cursor: pointer;
}
.pagination button:disabled {
  opacity: 0.4;
  cursor: default;
}
</style>
