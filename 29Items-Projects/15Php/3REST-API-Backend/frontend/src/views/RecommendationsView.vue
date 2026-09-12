<script setup>
import { onMounted, ref } from 'vue'
import api from '@/services/api'

const recommendations = ref([])
const loading = ref(false)
const error = ref(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    const { data } = await api.get('/recommendations', { params: { limit: 12 } })
    recommendations.value = data.data
  } catch (e) {
    error.value = e.message ?? 'Failed to load recommendations.'
  } finally {
    loading.value = false
  }
}

onMounted(load)

function formatPrice(p) {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: p.currency ?? 'USD' }).format(p.price)
}

const reasonLabel = (r) => (r === 'popular' ? 'Popular' : 'Based on your activity')
</script>

<template>
  <section>
    <div class="toolbar">
      <h2>Recommended for you</h2>
      <button class="btn btn--ghost" @click="load">Refresh</button>
    </div>

    <p v-if="loading" class="state" role="status">Finding products you might like…</p>

    <div v-else-if="error" class="state" role="alert">
      <p class="alert alert--error">{{ error }}</p>
      <button class="btn btn--ghost" @click="load">Retry</button>
    </div>

    <p v-else-if="recommendations.length === 0" class="state">
      No recommendations yet — browse and buy a few products to personalize this list.
    </p>

    <ul v-else class="grid">
      <li v-for="rec in recommendations" :key="rec.product_id" class="card">
        <span class="badge badge--info">{{ reasonLabel(rec.reason) }}</span>
        <h3>{{ rec.product.name }}</h3>
        <p class="muted">{{ rec.product.category }}</p>
        <p class="price">{{ formatPrice(rec.product) }}</p>
      </li>
    </ul>
  </section>
</template>
