<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  createInventory,
  fetchInventory,
  fetchInventoryByBarcode,
  fetchWarehouses,
  updateInventory,
} from '../api/inventory'
import AlertsPanel from '../components/AlertsPanel.vue'
import BarcodeInput from '../components/BarcodeInput.vue'
import OfflineQueueStatus from '../components/OfflineQueueStatus.vue'
import InventoryTable from '../components/inventory/InventoryTable.vue'
import ItemDetails from '../components/inventory/ItemDetails.vue'
import ItemForm from '../components/inventory/ItemForm.vue'
import StockActionPanel from '../components/stock/StockActionPanel.vue'
import WarehousePanel from '../components/warehouse/WarehousePanel.vue'
import { useAuth } from '../composables/useAuth'
import type { InventoryInput, InventoryItem, Warehouse } from '../types/inventory'

type Tab = 'inventory' | 'warehouses' | 'alerts'
const tab = ref<Tab>('inventory')
const items = ref<InventoryItem[]>([]),
  warehouses = ref<Warehouse[]>([])
const selected = ref<InventoryItem>(),
  editing = ref<InventoryItem>(),
  stockItem = ref<InventoryItem>()
const showForm = ref(false),
  loading = ref(false),
  error = ref(''),
  query = ref(''),
  warehouseId = ref('')
const { token, canWrite, isAdmin, setToken } = useAuth()
const activeWarehouses = computed(() => warehouses.value.filter((value) => value.active))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [inventoryPage, warehousePage] = await Promise.all([
      fetchInventory({
        query: query.value || undefined,
        warehouseId: warehouseId.value || undefined,
        size: 100,
      }),
      fetchWarehouses(),
    ])
    items.value = inventoryPage.content
    warehouses.value = warehousePage.content
    if (selected.value) selected.value = items.value.find((item) => item.id === selected.value?.id)
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : 'Could not load inventory.'
  } finally {
    loading.value = false
  }
}

async function scan(barcode: string) {
  error.value = ''
  try {
    selected.value = await fetchInventoryByBarcode(barcode, warehouseId.value || undefined)
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : 'Barcode was not found.'
  }
}

async function save(input: InventoryInput) {
  error.value = ''
  try {
    selected.value = editing.value
      ? await updateInventory(editing.value.id, input)
      : await createInventory(input)
    showForm.value = false
    editing.value = undefined
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : 'Could not save the item.'
  }
}

function edit(item?: InventoryItem) {
  editing.value = item
  showForm.value = true
}
onMounted(load)
</script>

<template>
  <div class="min-h-screen bg-slate-50 text-slate-900">
    <header class="border-b bg-slate-950 text-white">
      <div
        class="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-4 px-4 py-5 sm:px-6"
      >
        <div>
          <p class="text-xs font-semibold uppercase tracking-[0.2em] text-blue-300">
            Warehouse operations
          </p>
          <h1 class="text-2xl font-bold">Inventory Management</h1>
        </div>
        <details class="relative">
          <summary class="cursor-pointer text-sm">Access token</summary>
          <div
            class="absolute right-0 z-20 mt-2 w-80 rounded bg-white p-3 text-slate-900 shadow-xl"
          >
            <label class="text-xs"
              >OIDC bearer token<textarea
                :value="token"
                class="field h-24"
                placeholder="Optional in local mode"
                @change="setToken(($event.target as HTMLTextAreaElement).value)"
              ></textarea>
            </label>
          </div>
        </details>
      </div>
    </header>
    <main class="mx-auto max-w-7xl space-y-5 px-4 py-6 sm:px-6">
      <nav class="flex flex-wrap gap-2" aria-label="Primary">
        <button class="tab" :class="tab === 'inventory' && 'tab-active'" @click="tab = 'inventory'">
          Inventory
        </button>
        <button
          v-if="isAdmin"
          class="tab"
          :class="tab === 'warehouses' && 'tab-active'"
          @click="tab = 'warehouses'"
        >
          Warehouses
        </button>
        <button class="tab" :class="tab === 'alerts' && 'tab-active'" @click="tab = 'alerts'">
          Low-stock alerts
        </button>
      </nav>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <template v-if="tab === 'inventory'">
        <section class="card space-y-4">
          <BarcodeInput @scan="scan" />
          <form
            class="grid gap-3 sm:grid-cols-[2fr_1fr_auto]"
            aria-label="Inventory filters"
            @submit.prevent="load"
          >
            <label
              >Search<input v-model="query" class="field" placeholder="SKU, name, or barcode"
            /></label>
            <label
              >Warehouse<select v-model="warehouseId" class="field">
                <option value="">All warehouses</option>
                <option
                  v-for="warehouse in activeWarehouses"
                  :key="warehouse.id"
                  :value="warehouse.id"
                >
                  {{ warehouse.code }} - {{ warehouse.name }}
                </option>
              </select></label
            >
            <button class="btn-secondary self-end">Apply filters</button>
          </form>
          <div class="flex flex-wrap items-center justify-between gap-3">
            <OfflineQueueStatus @flushed="load" /><button
              v-if="canWrite"
              class="btn-primary"
              @click="edit()"
            >
              Add item
            </button>
          </div>
        </section>
        <p v-if="loading" class="card" role="status">Loading inventory...</p>
        <p v-else-if="!items.length" class="card text-slate-600">
          No inventory items match the current filters.
        </p>
        <InventoryTable
          v-else
          :items="items"
          :selected-id="selected?.id"
          @select="selected = $event"
          @edit="edit"
        />
        <div v-if="selected" class="flex justify-end">
          <button v-if="canWrite" class="btn-primary" @click="stockItem = selected">
            Perform stock operation
          </button>
        </div>
        <ItemDetails
          v-if="selected"
          :item="selected"
          @changed="load"
          @close="selected = undefined"
        />
      </template>
      <WarehousePanel v-else-if="tab === 'warehouses'" :warehouses="warehouses" @changed="load" />
      <AlertsPanel v-else />
    </main>
    <div
      v-if="showForm || stockItem"
      class="fixed inset-0 z-30 overflow-y-auto bg-slate-950/60 p-4"
      role="dialog"
      aria-modal="true"
    >
      <div class="mx-auto mt-8 max-w-2xl">
        <ItemForm
          v-if="showForm"
          :item="editing"
          :warehouses="activeWarehouses"
          @submit="save"
          @cancel="showForm = false"
        />
        <StockActionPanel
          v-else-if="stockItem"
          :item="stockItem"
          :warehouses="activeWarehouses"
          @changed="load"
          @close="stockItem = undefined"
        />
      </div>
    </div>
  </div>
</template>
