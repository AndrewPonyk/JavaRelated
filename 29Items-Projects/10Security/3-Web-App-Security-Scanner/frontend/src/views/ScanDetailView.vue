<script setup>
/**
 * Scan detail: full lifecycle view of one scan — status, live progress
 * (polled while running), per-severity counts, failure diagnostics,
 * cancel, findings for this scan, and authenticated report downloads.
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { api, ApiError } from '../api/client.js'
import { canScan } from '../stores/auth.js'
import FindingsTable from '../components/FindingsTable.vue'

const props = defineProps({
  id: { type: String, required: true },
})

const scanId = computed(() => Number(props.id))

const scan = ref(null)
const progress = ref(null)
const counts = ref({})
const errorMsg = ref('')
const actionMsg = ref('')
const cancelling = ref(false)
const downloading = ref('')
const pollTimer = ref(null)

const isRunning = computed(() => ['pending', 'running'].includes(scan.value?.status))

const failureDetail = computed(() => {
  if (!scan.value?.error_detail) return null
  try {
    const parsed = JSON.parse(scan.value.error_detail)
    return parsed.reason ?? parsed.phase ?? scan.value.error_detail
  } catch {
    return scan.value.error_detail
  }
})

async function loadScan() {
  try {
    scan.value = await api.getScan(scanId.value)
    counts.value = await api.scanFindingCounts(scanId.value)
    schedulePolling()
  } catch (err) {
    errorMsg.value = err instanceof ApiError ? err.message : 'Failed to load scan'
  }
}

function schedulePolling() {
  clearInterval(pollTimer.value)
  if (!isRunning.value) return
  pollTimer.value = setInterval(async () => {
    try {
      progress.value = await api.scanProgress(scanId.value)
      if (progress.value.phase === 'done' || progress.value.phase === 'failed') {
        clearInterval(pollTimer.value)
        await loadScan()
      }
    } catch {
      clearInterval(pollTimer.value)
    }
  }, 3000 + Math.random() * 500)
}

async function cancel() {
  cancelling.value = true
  actionMsg.value = ''
  try {
    scan.value = await api.cancelScan(scanId.value)
    clearInterval(pollTimer.value)
  } catch (err) {
    actionMsg.value = err instanceof ApiError ? err.message : 'Cancel failed'
  } finally {
    cancelling.value = false
  }
}

async function download(format) {
  downloading.value = format
  actionMsg.value = ''
  try {
    await api.downloadReport(scanId.value, format)
  } catch (err) {
    actionMsg.value = err instanceof ApiError ? err.message : 'Report download failed'
  } finally {
    downloading.value = ''
  }
}

const SEVERITIES = ['critical', 'high', 'medium', 'low', 'info']
const totalFindings = computed(() =>
  Object.values(counts.value).reduce((sum, n) => sum + n, 0),
)

onMounted(loadScan)
watch(scanId, loadScan)
onBeforeUnmount(() => clearInterval(pollTimer.value))
</script>

<template>
  <p v-if="errorMsg" class="banner banner-error" role="alert">{{ errorMsg }}</p>

  <section v-if="scan" class="panel">
    <div class="head">
      <h2>Scan #{{ scan.id }} <span class="status" :class="`st-${scan.status}`">{{ scan.status }}</span></h2>
      <div class="actions">
        <button
          v-if="isRunning && canScan()"
          type="button"
          class="cancel"
          :disabled="cancelling"
          @click="cancel"
        >
          {{ cancelling ? 'Cancelling…' : 'Cancel scan' }}
        </button>
        <button
          type="button"
          :disabled="downloading === 'html'"
          @click="download('html')"
        >
          {{ downloading === 'html' ? 'Preparing…' : '⬇ HTML report' }}
        </button>
        <button
          type="button"
          :disabled="downloading === 'sarif'"
          @click="download('sarif')"
        >
          {{ downloading === 'sarif' ? 'Preparing…' : '⬇ SARIF' }}
        </button>
      </div>
    </div>

    <dl class="meta">
      <div><dt>Target</dt><dd class="wrap">{{ scan.target_url }}</dd></div>
      <div><dt>Profile</dt><dd>{{ scan.profile }}</dd></div>
      <div><dt>Started</dt><dd>{{ scan.started_at ?? '—' }}</dd></div>
      <div><dt>Finished</dt><dd>{{ scan.finished_at ?? '—' }}</dd></div>
    </dl>

    <p v-if="failureDetail" class="banner banner-error" role="alert">
      Scan failed: {{ failureDetail }} — findings from completed phases are kept below.
    </p>
    <p v-if="actionMsg" class="banner banner-error" role="alert">{{ actionMsg }}</p>

    <template v-if="isRunning && progress">
      <div class="progress-track">
        <div class="progress-fill" :style="{ width: progress.percent + '%' }"></div>
      </div>
      <p class="progress-label">{{ progress.phase }} · {{ progress.percent }}%</p>
    </template>

    <div v-if="totalFindings" class="counts">
      <span v-for="s in SEVERITIES" :key="s" class="count-chip" :class="`sev-${s}`">
        {{ s }}: {{ counts[s] ?? 0 }}
      </span>
    </div>
  </section>

  <FindingsTable v-if="scan" :scan-id="scanId" />
</template>

<style scoped>
.panel {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 16px 20px;
}
.head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
h2 { margin: 0; font-size: 1.1rem; display: flex; align-items: center; gap: 10px; }
.actions { margin-left: auto; display: flex; gap: 8px; flex-wrap: wrap; }
.actions button {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  color: var(--text);
  padding: 5px 12px;
  cursor: pointer;
  font-size: 0.85rem;
}
.actions button:disabled { opacity: 0.5; cursor: wait; }
.actions .cancel { border-color: #5e2f1e; color: #ff9d7a; }
.status {
  font-size: 0.72rem;
  text-transform: uppercase;
  padding: 2px 10px;
  border-radius: 999px;
  border: 1px solid var(--border);
}
.st-running, .st-pending { color: #ffd76e; }
.st-completed { color: #7ee2a8; }
.st-failed { color: #ff8a8a; }
.st-cancelled { color: var(--muted); }
.meta { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 10px; margin: 14px 0; }
.meta div { display: flex; flex-direction: column; gap: 2px; }
.meta dt { color: var(--muted); font-size: 0.72rem; text-transform: uppercase; }
.meta dd { margin: 0; font-size: 0.9rem; }
.meta .wrap { word-break: break-all; }
.progress-track {
  height: 10px; border-radius: 999px;
  background: var(--bg); overflow: hidden; border: 1px solid var(--border);
}
.progress-fill { height: 100%; background: var(--accent); transition: width 0.6s ease; }
.progress-label { color: var(--muted); font-size: 0.88rem; margin: 8px 0 0; }
.counts { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 14px; }
.count-chip {
  font-size: 0.78rem;
  padding: 3px 10px;
  border-radius: 999px;
  border: 1px solid var(--border);
  text-transform: capitalize;
}
.sev-info { color: #aebbdd; }
.sev-low { color: #7ee2a8; }
.sev-medium { color: #ffd76e; }
.sev-high { color: #ff9d7a; }
.sev-critical { color: #ff7a9e; }
.banner { border-radius: 8px; padding: 10px 14px; font-size: 0.9rem; }
.banner-error { background: #3a1e26; color: #ff8a8a; border: 1px solid #5e2f1e; }
</style>
