<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import {
  completeReservation,
  fetchForecast,
  fetchMovements,
  fetchReservations,
  refreshForecast,
} from '../../api/inventory'
import type { Forecast, InventoryItem, Reservation, StockMovement } from '../../types/inventory'

const props = defineProps<{ item: InventoryItem }>()
const emit = defineEmits<{ changed: []; close: [] }>()
const movements = ref<StockMovement[]>([]),
  reservations = ref<Reservation[]>([])
const forecast = ref<Forecast | null>(null),
  loading = ref(false),
  error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  const [movementResult, reservationResult, forecastResult] = await Promise.allSettled([
    fetchMovements(props.item.id),
    fetchReservations(props.item.id),
    fetchForecast(props.item.id),
  ])
  movements.value = movementResult.status === 'fulfilled' ? movementResult.value.content : []
  reservations.value =
    reservationResult.status === 'fulfilled' ? reservationResult.value.content : []
  forecast.value = forecastResult.status === 'fulfilled' ? forecastResult.value : null
  loading.value = false
}

async function runForecast() {
  try {
    forecast.value = await refreshForecast(props.item.id)
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : 'Forecast failed.'
  }
}
async function finish(reservation: Reservation, action: 'release' | 'fulfill') {
  try {
    await completeReservation(reservation.id, action)
    await load()
    emit('changed')
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : 'Reservation update failed.'
  }
}
watch(() => props.item.id, load)
onMounted(load)
</script>

<template>
  <section class="card space-y-5">
    <div class="flex justify-between">
      <div>
        <h2 class="text-xl font-bold">{{ item.name }}</h2>
        <p class="text-sm text-slate-600">{{ item.sku }} · {{ item.warehouseCode }}</p>
      </div>
      <button @click="emit('close')">✕</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="loading" role="status">Loading details…</p>
    <dl class="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
      <div>
        <dt>On hand</dt>
        <dd>{{ item.quantity }}</dd>
      </div>
      <div>
        <dt>Reserved</dt>
        <dd>{{ item.reservedQuantity }}</dd>
      </div>
      <div>
        <dt>Available</dt>
        <dd>{{ item.availableQuantity }}</dd>
      </div>
      <div>
        <dt>Reorder at</dt>
        <dd>{{ item.reorderPoint }}</dd>
      </div>
    </dl>
    <div>
      <div class="flex items-center justify-between">
        <h3 class="font-bold">Demand forecast</h3>
        <button class="text-sm font-semibold text-blue-700" @click="runForecast">Refresh</button>
      </div>
      <p v-if="forecast" class="mt-2 text-sm">
        Next {{ forecast.horizonDays }} days: <strong>{{ forecast.predictedDemand }}</strong> units
        ({{ forecast.lowerBound }}–{{ forecast.upperBound }}), model {{ forecast.modelVersion }}
      </p>
      <p v-else class="text-sm text-slate-500">No forecast yet.</p>
    </div>
    <div>
      <h3 class="font-bold">Active reservations</h3>
      <div
        v-if="!reservations.some((value) => value.status === 'ACTIVE')"
        class="text-sm text-slate-500"
      >
        None
      </div>
      <div
        v-for="reservation in reservations.filter((value) => value.status === 'ACTIVE')"
        :key="reservation.id"
        class="mt-2 flex flex-wrap items-center justify-between rounded border p-2 text-sm"
      >
        <span>{{ reservation.externalReference }} · {{ reservation.quantity }} units</span
        ><span class="space-x-2"
          ><button class="font-semibold text-blue-700" @click="finish(reservation, 'fulfill')">
            Fulfill</button
          ><button class="font-semibold text-red-700" @click="finish(reservation, 'release')">
            Release
          </button></span
        >
      </div>
    </div>
    <div>
      <h3 class="font-bold">Recent movements</h3>
      <div class="max-h-64 overflow-auto">
        <table class="mt-2 w-full text-sm">
          <tbody>
            <tr v-for="movement in movements" :key="movement.id" class="border-t">
              <td class="py-2">{{ movement.type }}</td>
              <td
                class="text-right"
                :class="movement.quantityDelta < 0 ? 'text-red-700' : 'text-emerald-700'"
              >
                {{ movement.quantityDelta > 0 ? '+' : '' }}{{ movement.quantityDelta }}
              </td>
              <td class="pl-3 text-right text-slate-500">
                {{ new Date(movement.occurredAt).toLocaleString() }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>
