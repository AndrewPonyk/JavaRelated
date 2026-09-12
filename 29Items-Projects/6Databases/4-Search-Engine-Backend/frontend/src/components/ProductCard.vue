<script setup lang="ts">
import { computed } from "vue";

import type { ProductHit } from "@/types/search";

const props = defineProps<{ hit: ProductHit }>();
const emit = defineEmits<{ select: [] }>();

const price = computed(() =>
  new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(props.hit.price),
);
</script>

<template>
  <article
    class="card"
    role="button"
    tabindex="0"
    @click="emit('select')"
    @keydown.enter="emit('select')"
  >
    <p
      v-if="hit.brand"
      class="brand"
    >
      {{ hit.brand }}
    </p>
    <h3>{{ hit.name }}</h3>
    <p class="price">
      {{ price }}
    </p>
    <p
      v-if="!hit.in_stock"
      class="stock"
    >
      Out of stock
    </p>
  </article>
</template>

<style scoped>
.card {
  padding: 1rem;
  border: 1px solid #e2e8f0;
  border-radius: 0.75rem;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  cursor: pointer;
  transition: box-shadow 0.15s ease;
}
.card:hover,
.card:focus-visible {
  box-shadow: 0 4px 14px rgb(0 0 0 / 0.08);
  outline: none;
}
.brand {
  margin: 0;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #64748b;
}
h3 {
  margin: 0;
  font-size: 0.95rem;
  line-height: 1.35;
}
.price {
  margin: auto 0 0;
  font-weight: 700;
}
.stock {
  margin: 0;
  color: #dc2626;
  font-size: 0.8rem;
}
</style>
