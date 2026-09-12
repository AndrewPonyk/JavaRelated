<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ApiError } from '../../api/client'
import { createReservation, stockCommand, transferStock } from '../../api/inventory'
import { enqueueCommand } from '../../composables/offlineQueue'
import type { InventoryItem, Warehouse } from '../../types/inventory'

const props = defineProps<{ item: InventoryItem; warehouses: Warehouse[] }>()
const emit = defineEmits<{ changed: []; close: [] }>()
const action = ref<'adjustments' | 'receipts' | 'shipments' | 'reservation' | 'transfer'>(
  'adjustments',
)
const form = reactive({
  quantity: 1,
  delta: 0,
  reason: '',
  reference: '',
  destinationWarehouseId: '',
})
const busy = ref(false)
const message = ref('')
const error = ref('')
const destinations = computed(() =>
  props.warehouses.filter((value) => value.active && value.id !== props.item.warehouseId),
)

async function submit() {
  error.value = ''
  message.value = ''
  busy.value = true
  try {
    if (!form.reason.trim()) throw new Error('A reason is required.')
    if (action.value === 'reservation') {
      await createReservation(props.item.id, {
        quantity: form.quantity,
        externalReference: form.reference || `UI-${Date.now()}`,
        reason: form.reason,
      })
    } else if (action.value === 'transfer') {
      if (!form.destinationWarehouseId) throw new Error('Select a destination warehouse.')
      await transferStock({
        sourceItemId: props.item.id,
        destinationWarehouseId: form.destinationWarehouseId,
        quantity: form.quantity,
        reason: form.reason,
        reference: form.reference || undefined,
      })
    } else {
      const body =
        action.value === 'adjustments'
          ? { delta: form.delta, reason: form.reason, reference: form.reference || undefined }
          : { quantity: form.quantity, reason: form.reason, reference: form.reference || undefined }
      try {
        await stockCommand(props.item.id, action.value, body)
      } catch (reason) {
        if (reason instanceof ApiError && reason.status === 0) {
          enqueueCommand(props.item.id, action.value, body)
          message.value =
            'Network unavailable. Command saved and will retry with the same idempotency key.'
          return
        }
        throw reason
      }
    }
    message.value = 'Stock operation completed.'
    emit('changed')
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : 'Operation failed.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <form class="card space-y-4" @submit.prevent="submit">
    <div class="flex justify-between">
      <div>
        <h2 class="text-xl font-bold">Stock operation</h2>
        <p class="text-sm text-slate-600">
          {{ item.sku }} · {{ item.availableQuantity }} available
        </p>
      </div>
      <button type="button" @click="emit('close')">✕</button>
    </div>
    <div class="flex flex-wrap gap-2">
      <button
        v-for="value in [
          'adjustments',
          'receipts',
          'shipments',
          'reservation',
          'transfer',
        ] as const"
        :key="value"
        type="button"
        class="tab"
        :class="action === value ? 'tab-active' : ''"
        @click="action = value"
      >
        {{ value }}
      </button>
    </div>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="message" class="success" role="status">{{ message }}</p>
    <label v-if="action === 'adjustments'"
      >Quantity delta<input v-model.number="form.delta" class="field" type="number" required
    /></label>
    <label v-else
      >Quantity<input v-model.number="form.quantity" class="field" type="number" min="1" required
    /></label>
    <label v-if="action === 'transfer'"
      >Destination<select v-model="form.destinationWarehouseId" class="field" required>
        <option value="">Select…</option>
        <option v-for="warehouse in destinations" :key="warehouse.id" :value="warehouse.id">
          {{ warehouse.code }} — {{ warehouse.name }}
        </option>
      </select></label
    >
    <label>Reason<textarea v-model="form.reason" class="field" maxlength="250" required /></label>
    <label>Reference<input v-model="form.reference" class="field" maxlength="128" /></label>
    <button class="btn-primary w-full" :disabled="busy">
      {{ busy ? 'Applying…' : 'Apply operation' }}
    </button>
  </form>
</template>
