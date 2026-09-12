<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { flushQueue, readQueue } from '../composables/offlineQueue'

const count = ref(0)
const syncing = ref(false)
function refresh() {
  count.value = readQueue().length
}
async function sync() {
  syncing.value = true
  try {
    await flushQueue()
    refresh()
  } finally {
    syncing.value = false
  }
}
onMounted(() => {
  refresh()
  window.addEventListener('online', sync)
  window.addEventListener('inventory-queue-changed', refresh)
})
onBeforeUnmount(() => {
  window.removeEventListener('online', sync)
  window.removeEventListener('inventory-queue-changed', refresh)
})
</script>

<template>
  <button
    v-if="count"
    class="rounded-full bg-amber-100 px-3 py-1 text-sm font-semibold text-amber-900"
    :disabled="syncing"
    @click="sync"
  >
    {{ syncing ? 'Syncing…' : `${count} offline command${count === 1 ? '' : 's'}` }}
  </button>
</template>
