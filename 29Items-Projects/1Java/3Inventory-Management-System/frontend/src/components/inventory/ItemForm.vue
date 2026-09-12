<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type {
  BarcodeSymbology,
  InventoryInput,
  InventoryItem,
  Warehouse,
} from '../../types/inventory'

const props = defineProps<{ item?: InventoryItem; warehouses: Warehouse[] }>()
const emit = defineEmits<{ submit: [input: InventoryInput]; cancel: [] }>()
const symbologies: BarcodeSymbology[] = [
  'EAN_8',
  'EAN_13',
  'UPC_A',
  'CODE_39',
  'CODE_128',
  'QR_CODE',
  'UNKNOWN',
]
const error = ref('')
const form = reactive({
  sku: '',
  name: '',
  warehouseId: '',
  barcode: '',
  symbology: 'UNKNOWN' as BarcodeSymbology,
  aliases: '',
  quantity: 0,
  reorderPoint: 0,
  active: true,
})

watch(
  () => props.item,
  (item) => {
    Object.assign(
      form,
      item
        ? {
            sku: item.sku,
            name: item.name,
            warehouseId: item.warehouseId,
            barcode: item.barcode,
            symbology: item.barcodes.find((value) => value.primary)?.symbology ?? 'UNKNOWN',
            aliases: item.barcodes
              .filter((value) => !value.primary)
              .map((value) => value.barcode)
              .join(', '),
            quantity: item.quantity,
            reorderPoint: item.reorderPoint,
            active: item.active,
          }
        : {
            sku: '',
            name: '',
            warehouseId: props.warehouses.find((value) => value.active)?.id ?? '',
            barcode: '',
            symbology: 'UNKNOWN',
            aliases: '',
            quantity: 0,
            reorderPoint: 0,
            active: true,
          },
    )
  },
  { immediate: true },
)

const title = computed(() => (props.item ? `Edit ${props.item.sku}` : 'Add inventory item'))

function submit() {
  error.value = ''
  if (!form.sku.trim() || !form.name.trim() || !form.warehouseId || !form.barcode.trim()) {
    error.value = 'SKU, name, warehouse, and primary barcode are required.'
    return
  }
  if (form.quantity < 0 || form.reorderPoint < 0) {
    error.value = 'Quantities cannot be negative.'
    return
  }
  emit('submit', {
    sku: form.sku.trim().toUpperCase(),
    name: form.name.trim(),
    warehouseId: form.warehouseId,
    barcode: form.barcode.trim(),
    symbology: form.symbology,
    aliases: form.aliases
      .split(',')
      .map((value) => value.trim())
      .filter(Boolean)
      .map((barcode) => ({ barcode, symbology: 'UNKNOWN' as BarcodeSymbology, primary: false })),
    quantity: form.quantity,
    reorderPoint: form.reorderPoint,
    active: form.active,
    version: props.item?.version,
  })
}
</script>

<template>
  <form class="card space-y-4" @submit.prevent="submit">
    <div class="flex items-center justify-between">
      <h2 class="text-xl font-bold">{{ title }}</h2>
      <button type="button" @click="emit('cancel')">✕</button>
    </div>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div class="grid gap-4 sm:grid-cols-2">
      <label
        >SKU<input v-model="form.sku" class="field" :disabled="!!item" maxlength="64" required
      /></label>
      <label>Name<input v-model="form.name" class="field" maxlength="160" required /></label>
      <label
        >Warehouse<select v-model="form.warehouseId" class="field" :disabled="!!item" required>
          <option value="">Select…</option>
          <option v-for="warehouse in warehouses" :key="warehouse.id" :value="warehouse.id">
            {{ warehouse.code }} — {{ warehouse.name }}
          </option>
        </select></label
      >
      <label
        >Primary barcode<input v-model="form.barcode" class="field" maxlength="128" required
      /></label>
      <label
        >Symbology<select v-model="form.symbology" class="field">
          <option v-for="value in symbologies" :key="value">{{ value }}</option>
        </select></label
      >
      <label
        >Alias barcodes<input v-model="form.aliases" class="field" placeholder="Comma separated"
      /></label>
      <label v-if="!item"
        >Initial quantity<input
          v-model.number="form.quantity"
          class="field"
          type="number"
          min="0"
          required
      /></label>
      <label
        >Reorder point<input
          v-model.number="form.reorderPoint"
          class="field"
          type="number"
          min="0"
          required
      /></label>
      <label v-if="item" class="flex items-center gap-2"
        ><input v-model="form.active" type="checkbox" />Active</label
      >
    </div>
    <div class="flex justify-end gap-2">
      <button type="button" class="btn-secondary" @click="emit('cancel')">Cancel</button
      ><button class="btn-primary">Save</button>
    </div>
  </form>
</template>
