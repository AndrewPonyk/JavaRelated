<script setup lang="ts">
import { ref } from "vue";

import type { FacetGroup } from "@/types/search";

const props = defineProps<{
  facets: FacetGroup[];
  priceMin: number | null;
  priceMax: number | null;
}>();

const emit = defineEmits<{
  toggle: [group: string, value: string];
  clear: [];
  "apply-price": [min: number | null, max: number | null];
}>();

// Vue auto-applies the .number modifier to type="number" inputs, so these refs
// hold a number once filled and "" when cleared.
const minInput = ref<string | number>(props.priceMin ?? "");
const maxInput = ref<string | number>(props.priceMax ?? "");
const priceError = ref<string | null>(null);

function parseAmount(raw: string | number): number | null {
  if (typeof raw === "number") return Number.isFinite(raw) ? raw : NaN;
  if (raw.trim() === "") return null;
  const value = Number(raw);
  return Number.isFinite(value) ? value : NaN;
}

function applyPrice(): void {
  const min = parseAmount(minInput.value);
  const max = parseAmount(maxInput.value);
  if (Number.isNaN(min) || Number.isNaN(max)) {
    priceError.value = "Enter valid numbers.";
    return;
  }
  if ((min ?? 0) < 0 || (max ?? 0) < 0) {
    priceError.value = "Prices cannot be negative.";
    return;
  }
  if (min !== null && max !== null && min > max) {
    priceError.value = "Min must be ≤ max.";
    return;
  }
  priceError.value = null;
  emit("apply-price", min, max);
}

function clearAll(): void {
  minInput.value = "";
  maxInput.value = "";
  priceError.value = null;
  emit("clear");
}
</script>

<template>
  <aside class="facet-panel">
    <header>
      <h2>Filters</h2>
      <button
        type="button"
        class="clear"
        @click="clearAll"
      >
        Clear all
      </button>
    </header>

    <section
      v-for="group in facets"
      :key="group.name"
    >
      <h3>{{ group.label }}</h3>

      <template v-if="group.name !== 'price'">
        <label
          v-for="item in group.values"
          :key="item.value"
        >
          <input
            type="checkbox"
            :checked="item.selected"
            @change="emit('toggle', group.name, item.value)"
          >
          <span class="value">{{ item.value }}</span>
          <span class="count">{{ item.count }}</span>
        </label>
      </template>

      <template v-else>
        <p
          v-for="item in group.values"
          :key="item.value"
          class="price-bucket"
        >
          <span class="value">{{ item.value }}</span>
          <span class="count">{{ item.count }}</span>
        </p>
        <form
          class="price-form"
          @submit.prevent="applyPrice"
        >
          <input
            v-model="minInput"
            type="number"
            min="0"
            step="0.01"
            placeholder="Min"
            aria-label="Minimum price"
          >
          <span aria-hidden="true">–</span>
          <input
            v-model="maxInput"
            type="number"
            min="0"
            step="0.01"
            placeholder="Max"
            aria-label="Maximum price"
          >
          <button type="submit">
            Go
          </button>
        </form>
        <p
          v-if="priceError"
          class="price-error"
          role="alert"
        >
          {{ priceError }}
        </p>
      </template>
    </section>
  </aside>
</template>

<style scoped>
.facet-panel {
  min-width: 14rem;
}
header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
h2 {
  font-size: 1rem;
  margin: 0 0 0.5rem;
}
h3 {
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: #64748b;
  margin: 1rem 0 0.4rem;
}
label,
.price-bucket {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.2rem 0;
  margin: 0;
}
label {
  cursor: pointer;
}
.value {
  flex: 1;
  text-transform: capitalize;
}
.count {
  color: #94a3b8;
  font-size: 0.85rem;
}
.clear {
  border: none;
  background: none;
  color: #2563eb;
  cursor: pointer;
  font-size: 0.85rem;
}
.price-form {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin-top: 0.5rem;
}
.price-form input {
  width: 5rem;
  padding: 0.35rem 0.5rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.375rem;
}
.price-form button {
  padding: 0.35rem 0.7rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.375rem;
  background: #fff;
  cursor: pointer;
}
.price-error {
  color: #dc2626;
  font-size: 0.8rem;
  margin: 0.35rem 0 0;
}
</style>
