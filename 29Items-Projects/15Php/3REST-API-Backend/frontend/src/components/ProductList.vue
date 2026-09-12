<script setup>
/**
 * Presentational product list: renders loading / error / empty / success
 * states and a responsive grid of ProductCard items. All data and actions
 * are owned by the parent view (props down, events up).
 */
import ProductCard from '@/components/ProductCard.vue'

defineProps({
  products: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  error: { type: String, default: null },
  busyId: { type: Number, default: null },
})

defineEmits(['buy', 'retry'])
</script>

<template>
  <p v-if="loading" class="state" role="status">Loading products…</p>

  <div v-else-if="error" class="state" role="alert">
    <p class="alert alert--error">{{ error }}</p>
    <button class="btn btn--ghost" @click="$emit('retry')">Retry</button>
  </div>

  <p v-else-if="products.length === 0" class="state">No products found.</p>

  <ul v-else class="grid">
    <ProductCard
      v-for="product in products"
      :key="product.id"
      :product="product"
      :busy="busyId === product.id"
      @buy="$emit('buy', $event)"
    />
  </ul>
</template>
