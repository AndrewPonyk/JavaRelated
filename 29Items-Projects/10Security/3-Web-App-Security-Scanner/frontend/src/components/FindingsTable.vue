<script setup>
/**
 * Findings browser — loading / empty / error / data states, severity and
 * OWASP filters, pagination via the backend FindingsPage contract, and a
 * row-click detail modal (evidence lives in the detail view only).
 */
import { onMounted, ref, watch } from 'vue'
import { api, ApiError } from '../api/client.js'
import SeverityBadge from './SeverityBadge.vue'
import FindingDetailModal from './FindingDetailModal.vue'

const props = defineProps({
  scanId: { type: Number, default: null },
})

const findings = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 25
const severityFilter = ref('')
const owaspFilter = ref('')
const loading = ref(false)
const errorMsg = ref('')
const selectedFindingId = ref(null)

const OWASP = ['A01:2021', 'A02:2021', 'A03:2021', 'A04:2021', 'A05:2021']

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const params = new URLSearchParams({ page: page.value, page_size: pageSize })
    if (props.scanId) params.set('scan_id', props.scanId)
    if (severityFilter.value) params.set('severity', severityFilter.value)
    if (owaspFilter.value) params.set('owasp_category', owaspFilter.value)
    const data = await api.listFindings(`?${params}`)
    findings.value = data.items
    total.value = data.total
  } catch (err) {
    errorMsg.value = err instanceof ApiError ? err.message : 'Unexpected error'
    findings.value = []
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  severityFilter.value = ''
  owaspFilter.value = ''
  page.value = 1
}

watch(severityFilter, () => (page.value = 1))
watch(owaspFilter, () => (page.value = 1))

onMounted(load)
watch([page, severityFilter, owaspFilter, () => props.scanId], load)

const pages = () => Math.max(1, Math.ceil(total.value / pageSize))
</script>

<template>
  <section class="panel">
    <div class="toolbar">
      <h2>Findings <span class="count">({{ total }})</span></h2>
      <div class="filters">
        <select v-model="severityFilter" aria-label="Filter by severity">
          <option value="">All severities</option>
          <option v-for="s in ['critical', 'high', 'medium', 'low', 'info']" :key="s" :value="s">
            {{ s }}
          </option>
        </select>
        <select v-model="owaspFilter" aria-label="Filter by OWASP category">
          <option value="">All OWASP</option>
          <option v-for="o in OWASP" :key="o" :value="o">{{ o }}</option>
        </select>
        <button
          v-if="severityFilter || owaspFilter"
          class="clear"
          type="button"
          @click="resetFilters"
        >
          Clear
        </button>
      </div>
    </div>

    <p v-if="loading" class="state">Loading findings…</p>
    <p v-else-if="errorMsg" class="state state-error" role="alert">{{ errorMsg }}</p>
    <p v-else-if="findings.length === 0" class="state state-empty">
      No findings{{ severityFilter || owaspFilter ? ' for the current filters' : '' }} —
      either a clean scan or nothing scanned yet.
    </p>

    <div v-else class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Severity</th><th>Title</th><th>Source</th>
            <th>OWASP</th><th>URL</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="f in findings"
            :key="f.id"
            class="row"
            tabindex="0"
            @click="selectedFindingId = f.id"
            @keydown.enter="selectedFindingId = f.id"
          >
            <td><SeverityBadge :severity="f.severity" /></td>
            <td class="title">
              {{ f.title }}
              <span v-if="f.severity_confidence != null" class="conf">
                ML {{ (f.severity_confidence * 100).toFixed(0) }}%
              </span>
            </td>
            <td><code>{{ f.source }}</code></td>
            <td>{{ f.owasp_category ?? '—' }}</td>
            <td class="url">{{ f.url }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="pages() > 1" class="pager">
      <button :disabled="page <= 1" @click="page--">‹ Prev</button>
      <span>Page {{ page }} / {{ pages() }}</span>
      <button :disabled="page >= pages()" @click="page++">Next ›</button>
    </div>
    <p class="hint">Click a row for full detail and evidence.</p>

    <FindingDetailModal
      v-if="selectedFindingId != null"
      :finding-id="selectedFindingId"
      @close="selectedFindingId = null"
    />
  </section>
</template>

<style scoped>
.panel {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 16px 20px;
}
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; }
h2 { margin: 0; font-size: 1.05rem; }
.count { color: var(--muted); font-weight: 400; }
.filters { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.filters select {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  color: var(--text);
  padding: 5px 8px;
  font-size: 0.85rem;
}
.clear {
  background: none;
  border: 1px solid var(--border);
  border-radius: 6px;
  color: var(--muted);
  padding: 4px 10px;
  cursor: pointer;
  font-size: 0.8rem;
}
.state { color: var(--muted); }
.state-error { color: #ff8a8a; }
.table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 0.88rem; }
th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--border); }
th { color: var(--muted); font-weight: 500; text-transform: uppercase; font-size: 0.72rem; }
.row { cursor: pointer; }
.row:hover, .row:focus { background: rgba(79, 140, 255, 0.07); outline: none; }
.url { max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conf { color: var(--muted); font-size: 0.75rem; margin-left: 6px; }
.pager { display: flex; align-items: center; gap: 12px; margin-top: 12px; }
.pager button {
  background: var(--bg); color: var(--text);
  border: 1px solid var(--border); border-radius: 6px; padding: 4px 12px; cursor: pointer;
}
.pager button:disabled { opacity: 0.4; cursor: default; }
.hint { color: var(--muted); font-size: 0.78rem; margin: 10px 0 0; }
</style>
