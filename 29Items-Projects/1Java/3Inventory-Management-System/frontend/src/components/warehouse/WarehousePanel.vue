<script setup lang="ts">
import { reactive, ref } from 'vue'
import { createWarehouse, updateWarehouse } from '../../api/inventory'
import type { Warehouse } from '../../types/inventory'

const props = defineProps<{ warehouses: Warehouse[] }>()
const emit = defineEmits<{ changed: [] }>()
const form = reactive({ code: '', name: '' }),
  error = ref(''),
  busy = ref(false)
async function create() {
  error.value = ''
  busy.value = true
  try {
    await createWarehouse(form)
    form.code = ''
    form.name = ''
    emit('changed')
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : 'Creation failed.'
  } finally {
    busy.value = false
  }
}
async function toggle(warehouse: Warehouse) {
  try {
    await updateWarehouse({ ...warehouse, active: !warehouse.active })
    emit('changed')
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : 'Update failed.'
  }
}
</script>

<template>
  <section class="space-y-5">
    <form class="card grid gap-3 sm:grid-cols-[1fr_2fr_auto]" @submit.prevent="create">
      <label
        >Code<input
          v-model="form.code"
          class="field"
          pattern="[A-Za-z0-9_-]+"
          maxlength="32"
          required /></label
      ><label>Name<input v-model="form.name" class="field" maxlength="120" required /></label
      ><button class="btn-primary self-end" :disabled="busy">Add warehouse</button>
      <p v-if="error" class="error sm:col-span-3">{{ error }}</p>
    </form>
    <div class="grid gap-3 sm:grid-cols-2">
      <article
        v-for="warehouse in props.warehouses"
        :key="warehouse.id"
        class="card flex items-center justify-between"
      >
        <div>
          <h3 class="font-bold">{{ warehouse.code }}</h3>
          <p>{{ warehouse.name }}</p>
          <p class="text-xs text-slate-500">{{ warehouse.active ? 'Active' : 'Inactive' }}</p>
        </div>
        <button class="btn-secondary" @click="toggle(warehouse)">
          {{ warehouse.active ? 'Deactivate' : 'Activate' }}
        </button>
      </article>
    </div>
  </section>
</template>
