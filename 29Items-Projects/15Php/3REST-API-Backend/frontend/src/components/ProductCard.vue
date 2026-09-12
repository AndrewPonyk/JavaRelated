<script setup>
defineProps({
  product: { type: Object, required: true },
  busy: { type: Boolean, default: false },
})

defineEmits(['buy'])

function formatPrice(p) {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: p.currency ?? 'USD',
  }).format(p.price)
}
</script>

<template>
  <li class="card">
    <h3>{{ product.name }}</h3>
    <p class="muted">{{ product.category }}</p>
    <p class="price">{{ formatPrice(product) }}</p>
    <span :class="['badge', product.stock > 0 ? 'badge--ok' : 'badge--off']">
      {{ product.stock > 0 ? `${product.stock} in stock` : 'Out of stock' }}
    </span>
    <button
      class="btn"
      style="margin-top: auto"
      :disabled="busy || product.stock < 1"
      @click="$emit('buy', product)"
    >
      {{ busy ? 'Buying…' : 'Buy 1' }}
    </button>
  </li>
</template>
