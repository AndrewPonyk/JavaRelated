<script setup lang="ts">
import type { InventoryItem } from '../../types/inventory'

defineProps<{ items: InventoryItem[]; selectedId?: string }>()
const emit = defineEmits<{ select: [item: InventoryItem]; edit: [item: InventoryItem] }>()
</script>

<template>
  <div class="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
    <table class="min-w-full divide-y divide-slate-200">
      <caption class="sr-only">
        Current warehouse inventory
      </caption>
      <thead class="bg-slate-100 text-left text-xs uppercase tracking-wide text-slate-600">
        <tr>
          <th class="px-4 py-3">Item</th>
          <th class="px-4 py-3">Warehouse</th>
          <th class="px-4 py-3">Barcode</th>
          <th class="px-4 py-3 text-right">On hand</th>
          <th class="px-4 py-3 text-right">Available</th>
          <th class="px-4 py-3">Status</th>
          <th class="px-4 py-3"><span class="sr-only">Actions</span></th>
        </tr>
      </thead>
      <tbody class="divide-y divide-slate-100">
        <tr
          v-for="item in items"
          :key="item.id"
          class="cursor-pointer hover:bg-blue-50"
          :class="selectedId === item.id ? 'bg-blue-50' : ''"
          @click="emit('select', item)"
        >
          <td class="px-4 py-3">
            <p class="font-medium">{{ item.name }}</p>
            <p class="text-xs text-slate-500">{{ item.sku }}</p>
          </td>
          <td class="px-4 py-3">{{ item.warehouseCode }}</td>
          <td class="px-4 py-3 font-mono text-sm">{{ item.barcode }}</td>
          <td class="px-4 py-3 text-right tabular-nums">{{ item.quantity }}</td>
          <td class="px-4 py-3 text-right tabular-nums">{{ item.availableQuantity }}</td>
          <td class="px-4 py-3">
            <span v-if="!item.active" class="badge bg-slate-200 text-slate-700">Inactive</span>
            <span v-else-if="item.lowStock" class="badge bg-amber-100 text-amber-900"
              >Low stock</span
            >
            <span v-else class="badge bg-emerald-100 text-emerald-900">In stock</span>
          </td>
          <td class="px-4 py-3 text-right">
            <button class="text-sm font-semibold text-blue-700" @click.stop="emit('edit', item)">
              Edit
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
