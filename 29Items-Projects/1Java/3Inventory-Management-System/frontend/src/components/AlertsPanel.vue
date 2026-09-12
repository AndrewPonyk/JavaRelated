<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { acknowledgeAlert, fetchAlerts } from '../api/inventory'
import type { LowStockAlert } from '../types/inventory'
const alerts = ref<LowStockAlert[]>([]),
  error = ref(''),
  loading = ref(false)
async function load() {
  loading.value = true
  try {
    alerts.value = (await fetchAlerts()).content
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : 'Could not load alerts.'
  } finally {
    loading.value = false
  }
}
async function acknowledge(id: string) {
  try {
    await acknowledgeAlert(id)
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : 'Could not acknowledge alert.'
  }
}
onMounted(load)
</script>

<template>
  <section class="space-y-3">
    <p v-if="loading">Loading alerts…</p>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="!loading && !alerts.length" class="card text-slate-600">No open low-stock alerts.</p>
    <article
      v-for="alert in alerts"
      :key="alert.id"
      class="card flex flex-wrap items-center justify-between gap-3"
    >
      <div>
        <h3 class="font-bold">
          {{ alert.itemName }} <span class="font-mono text-sm">{{ alert.sku }}</span>
        </h3>
        <p class="text-sm text-slate-600">
          {{ alert.warehouseCode }} · {{ alert.quantity }} available, reorder at
          {{ alert.reorderPoint }}
        </p>
      </div>
      <button class="btn-primary" @click="acknowledge(alert.id)">Acknowledge</button>
    </article>
  </section>
</template>
