<script setup lang="ts">
/**
 * Search input with debounced autocomplete (ARIA combobox pattern).
 * Suggestions are fetched 150 ms after the last keystroke; in-flight requests are
 * aborted on each new keystroke so stale dropdowns never render. Arrow keys move
 * the active option, Enter picks it, Escape closes the list.
 */
import { ref } from "vue";

import { fetchSuggestions } from "@/api/search";
import { useDebounceFn } from "@/composables/useDebounceFn";

const model = defineModel<string>({ default: "" });
const emit = defineEmits<{ submit: [] }>();

const suggestions = ref<string[]>([]);
const open = ref(false);
const activeIndex = ref(-1);
let controller: AbortController | null = null;

const loadSuggestions = useDebounceFn(async (prefix: string) => {
  controller?.abort();
  if (prefix.trim().length < 2) {
    close();
    return;
  }
  controller = new AbortController();
  try {
    suggestions.value = await fetchSuggestions(prefix, controller.signal);
    open.value = suggestions.value.length > 0;
    activeIndex.value = -1;
  } catch {
    // Suggestions are a nicety — fail silently, never block typing.
    close();
  }
}, 150);

function close(): void {
  suggestions.value = [];
  open.value = false;
  activeIndex.value = -1;
}

function onInput(): void {
  loadSuggestions(model.value);
}

function submit(): void {
  close();
  emit("submit");
}

function pick(suggestion: string): void {
  model.value = suggestion;
  submit();
}

function move(delta: number): void {
  if (!open.value || suggestions.value.length === 0) return;
  const count = suggestions.value.length;
  activeIndex.value = (activeIndex.value + delta + count) % count;
}

function onEnter(): void {
  if (open.value && activeIndex.value >= 0) {
    pick(suggestions.value[activeIndex.value]);
  } else {
    submit();
  }
}
</script>

<template>
  <div class="search-bar">
    <form
      role="search"
      @submit.prevent="onEnter"
    >
      <input
        v-model="model"
        type="search"
        placeholder="Search products…"
        autocomplete="off"
        role="combobox"
        aria-label="Search products"
        aria-autocomplete="list"
        aria-controls="search-suggestions"
        :aria-expanded="open"
        :aria-activedescendant="activeIndex >= 0 ? `suggestion-${activeIndex}` : undefined"
        @input="onInput"
        @blur="open = false"
        @keydown.down.prevent="move(1)"
        @keydown.up.prevent="move(-1)"
        @keydown.esc="close"
      >
      <button type="submit">
        Search
      </button>
    </form>

    <ul
      v-if="open"
      id="search-suggestions"
      class="suggestions"
      role="listbox"
    >
      <li
        v-for="(suggestion, index) in suggestions"
        :id="`suggestion-${index}`"
        :key="suggestion"
        role="option"
        :aria-selected="index === activeIndex"
      >
        <!-- mousedown fires before the input's blur closes the dropdown -->
        <button
          type="button"
          :class="{ active: index === activeIndex }"
          @mousedown.prevent="pick(suggestion)"
          @mousemove="activeIndex = index"
        >
          {{ suggestion }}
        </button>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.search-bar {
  position: relative;
  max-width: 40rem;
}
form {
  display: flex;
  gap: 0.5rem;
}
input {
  flex: 1;
  padding: 0.6rem 0.9rem;
  border: 1px solid #cbd5e1;
  border-radius: 0.5rem;
  font-size: 1rem;
}
form > button {
  padding: 0.6rem 1.2rem;
  border: none;
  border-radius: 0.5rem;
  background: #2563eb;
  color: #fff;
  cursor: pointer;
}
.suggestions {
  position: absolute;
  inset-inline: 0;
  top: 100%;
  margin: 0.25rem 0 0;
  padding: 0.25rem;
  list-style: none;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 0.5rem;
  box-shadow: 0 8px 20px rgb(0 0 0 / 0.08);
  z-index: 10;
}
.suggestions button {
  display: block;
  width: 100%;
  padding: 0.45rem 0.75rem;
  border: none;
  background: none;
  text-align: left;
  cursor: pointer;
  border-radius: 0.375rem;
}
.suggestions button:hover,
.suggestions button.active {
  background: #f1f5f9;
}
</style>
