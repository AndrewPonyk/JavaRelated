<script setup>
import { onMounted, ref } from 'vue'
import api from '@/services/api'

const orders = ref([])
const loading = ref(false)
const error = ref(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    const { data } = await api.get('/orders')
    orders.value = data.data
  } catch (e) {
    error.value = e.message ?? 'Failed to load orders.'
  } finally {
    loading.value = false
  }
}

onMounted(load)

function money(value, currency = 'USD') {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(value)
}
</script>

<template>
  <section>
    <div class="toolbar">
      <h2>Your orders</h2>
      <button class="btn btn--ghost" @click="load">Refresh</button>
    </div>

    <p v-if="loading" class="state" role="status">Loading orders…</p>

    <div v-else-if="error" class="state" role="alert">
      <p class="alert alert--error">{{ error }}</p>
      <button class="btn btn--ghost" @click="load">Retry</button>
    </div>

    <p v-else-if="orders.length === 0" class="state">You haven't placed any orders yet.</p>

    <div v-else>
      <article v-for="order in orders" :key="order.id" class="card" style="margin-bottom: 1rem">
        <div class="toolbar" style="margin-bottom: 0.5rem">
          <strong>Order #{{ order.id }}</strong>
          <span class="badge badge--ok">{{ order.status }}</span>
        </div>
        <ul style="margin: 0; padding-left: 1.1rem">
          <li v-for="(item, i) in order.items" :key="i" class="muted">
            {{ item.quantity }} × {{ item.product_name ?? `product #${item.product_id}` }}
            — {{ money(item.subtotal, order.currency) }}
          </li>
        </ul>
        <p class="price" style="margin: 0.5rem 0 0">Total: {{ money(order.total, order.currency) }}</p>
      </article>
    </div>
  </section>
</template>
