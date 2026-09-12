<script setup>
/**
 * Scan launch form — reference pattern for data fetching in this app:
 * three visible states (idle / submitting / error), validation before send,
 * API errors surfaced from the normalized ApiError envelope.
 */
import { reactive, ref } from 'vue'
import { api, ApiError } from '../api/client.js'

const emit = defineEmits(['scan-created'])

const form = reactive({
  target_url: '',
  profile: 'standard',
})
const submitting = ref(false)
const errorMsg = ref('')
const createdScanId = ref(null)

const PROFILES = [
  { value: 'fast', label: 'Fast — spider + passive' },
  { value: 'standard', label: 'Standard — active scan + SQLi' },
  { value: 'deep', label: 'Deep — full rules + deep SQLi' },
]

function isValidUrl(value) {
  try {
    const u = new URL(value)
    return u.protocol === 'http:' || u.protocol === 'https:'
  } catch {
    return false
  }
}

async function submit() {
  errorMsg.value = ''
  createdScanId.value = null

  if (!isValidUrl(form.target_url)) {
    errorMsg.value = 'Enter a valid http(s) target URL.'
    return
  }

  submitting.value = true
  try {
    const scan = await api.createScan({ ...form })
    createdScanId.value = scan.id
    emit('scan-created', scan)
    form.target_url = ''
  } catch (err) {
    errorMsg.value = err instanceof ApiError ? err.message : 'Unexpected error'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="panel">
    <h2>Launch a scan</h2>
    <form @submit.prevent="submit" novalidate>
      <div class="row">
        <input
          v-model="form.target_url"
          type="url"
          placeholder="https://app.example.com"
          aria-label="Target URL"
          :disabled="submitting"
          required
        />
        <select v-model="form.profile" aria-label="Scan profile" :disabled="submitting">
          <option v-for="p in PROFILES" :key="p.value" :value="p.value">{{ p.label }}</option>
        </select>
        <button type="submit" :disabled="submitting">
          {{ submitting ? 'Submitting…' : 'Scan' }}
        </button>
      </div>
    </form>

    <p v-if="submitting" class="state state-loading" role="status">
      Creating scan… you will be able to follow live progress below.
    </p>
    <p v-else-if="errorMsg" class="state state-error" role="alert">
      {{ errorMsg }}
    </p>
    <p v-else-if="createdScanId" class="state state-ok" role="status">
      ✅ Scan #{{ createdScanId }} queued — polling progress.
    </p>
  </section>
</template>

<style scoped>
.panel {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 16px 20px;
}
h2 { margin: 0 0 12px; font-size: 1.05rem; }
.row { display: flex; gap: 8px; flex-wrap: wrap; }
input[type='url'] { flex: 2 1 320px; min-width: 200px; }
select { flex: 1 1 220px; }
input, select {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  color: var(--text);
  padding: 9px 10px;
}
button {
  background: var(--accent);
  border: 0;
  border-radius: 6px;
  color: #fff;
  font-weight: 600;
  padding: 9px 22px;
  cursor: pointer;
}
button:disabled { opacity: 0.6; cursor: wait; }
.state { margin: 12px 0 0; font-size: 0.9rem; }
.state-error { color: #ff8a8a; }
.state-ok { color: #7ee2a8; }
.state-loading { color: var(--muted); }
</style>
