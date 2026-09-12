<script setup>
/**
 * Dashboard: launch scans (scanner/admin), watch the latest scan's live
 * progress (bounded polling — one interval for the whole view, jittered,
 * stops on terminal state; see TECH-NOTES pitfall #6), severity overview
 * and the findings browser.
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { api, ApiError } from '../api/client.js'
import { canScan } from '../stores/auth.js'
import ScanForm from '../components/ScanForm.vue'
import FindingsTable from '../components/FindingsTable.vue'

const latestScan = ref(null)
const progress = ref(null)
const stats = ref([])
const pollTimer = ref(null)
const errorMsg = ref('')
const cancelling = ref(false)

const selectedScanId = computed(() => latestScan.value?.id ?? null)
const isRunning = computed(() => ['pending', 'running'].includes(latestScan.value?.status))

async function refreshOverviews() {
  try {
    const [scans, statList] = await Promise.all([api.listScans(), api.severityStats()])
    latestScan.value = scans[0] ?? null
    stats.value = statList
    scheduleProgressPolling()
  } catch (err) {
    errorMsg.value = err instanceof ApiError ? err.message : 'Failed to load dashboard'
  }
}

function scheduleProgressPolling() {
  clearInterval(pollTimer.value)
  if (!isRunning.value) return
  pollTimer.value = setInterval(async () => {
    try {
      progress.value = await api.scanProgress(latestScan.value.id)
      if (progress.value.phase === 'done' || progress.value.phase === 'failed') {
        clearInterval(pollTimer.value)
        await refreshOverviews()
      }
    } catch {
      clearInterval(pollTimer.value) // stop stampede on API trouble
    }
  }, 3000 + Math.random() * 500) // jitter
}

function onScanCreated(scan) {
  latestScan.value = scan
  progress.value = null
  scheduleProgressPolling()
  refreshOverviews() // pull stats + list without waiting for terminal state
}

async function cancelLatest() {
  if (!latestScan.value || cancelling.value) return
  cancelling.value = true
  try {
    latestScan.value = await api.cancelScan(latestScan.value.id)
    clearInterval(pollTimer.value)
    await refreshOverviews()
  } catch (err) {
    errorMsg.value = err instanceof ApiError ? err.message : 'Cancel failed'
  } finally {
    cancelling.value = false
  }
}

onMounted(refreshOverviews)
onBeforeUnmount(() => clearInterval(pollTimer.value))
</script>

<template>
  <p v-if="errorMsg" class="banner banner-error" role="alert">{{ errorMsg }}</p>

  <ScanForm v-if="canScan()" @scan-created="onScanCreated" />
  <p v-else class="banner banner-info">
    Your account has viewer access — ask an admin to promote you to launch scans.
  </p>

  <section v-if="latestScan" class="panel">
    <div class="latest-head">
      <h2>
        Latest scan — <RouterLink :to="{ name: 'scan-detail', params: { id: latestScan.id } }">
          #{{ latestScan.id }}
        </RouterLink>
      </h2>
      <span class="status" :class="`st-${latestScan.status}`">{{ latestScan.status }}</span>
      <button
        v-if="isRunning && canScan()"
        class="cancel"
        type="button"
        :disabled="cancelling"
        @click="cancelLatest"
      >
        {{ cancelling ? 'Cancelling…' : 'Cancel' }}
      </button>
    </div>
    <p class="target">{{ latestScan.target_url }} · profile {{ latestScan.profile }}</p>

    <template v-if="progress">
      <div class="progress-track">
        <div class="progress-fill" :style="{ width: progress.percent + '%' }"></div>
      </div>
      <p class="progress-label">
        {{ progress.phase }} · {{ progress.percent }}%
        <template v-if="progress.findings_so_far">
          · {{ progress.findings_so_far }} findings so far
        </template>
      </p>
    </template>
  </section>

  <section class="panel stats">
    <h2>Severity overview</h2>
    <div v-if="stats.length" class="stats-row">
      <div v-for="s in stats" :key="s.severity" class="stat">
        <span class="stat-count">{{ s.count }}</span>
        <span class="stat-label">{{ s.severity }}</span>
      </div>
    </div>
    <p v-else class="empty">No findings recorded yet — launch your first scan above.</p>
  </section>

  <FindingsTable :scan-id="selectedScanId" />
</template>

<style scoped>
.panel {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 16px 20px;
}
h2 { margin: 0; font-size: 1.05rem; }
h2 a { color: var(--accent); }
.latest-head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.target { color: var(--muted); font-size: 0.88rem; margin: 6px 0 12px; word-break: break-all; }
.status {
  font-size: 0.75rem;
  text-transform: uppercase;
  padding: 2px 10px;
  border-radius: 999px;
  border: 1px solid var(--border);
}
.st-running, .st-pending { color: #ffd76e; }
.st-completed { color: #7ee2a8; }
.st-failed { color: #ff8a8a; }
.st-cancelled { color: var(--muted); }
.cancel {
  margin-left: auto;
  background: none;
  border: 1px solid #5e2f1e;
  color: #ff9d7a;
  border-radius: 6px;
  padding: 4px 12px;
  cursor: pointer;
}
.cancel:disabled { opacity: 0.5; cursor: wait; }
.progress-track {
  height: 10px; border-radius: 999px;
  background: var(--bg); overflow: hidden; border: 1px solid var(--border);
}
.progress-fill {
  height: 100%; background: var(--accent);
  transition: width 0.6s ease;
}
.progress-label { color: var(--muted); font-size: 0.88rem; margin: 8px 0 0; }
.stats-row { display: flex; gap: 14px; flex-wrap: wrap; }
.stat {
  flex: 1 1 100px; text-align: center;
  background: var(--bg); border: 1px solid var(--border); border-radius: 8px;
  padding: 12px 8px;
}
.stat-count { display: block; font-size: 1.5rem; font-weight: 700; }
.stat-label { color: var(--muted); font-size: 0.78rem; text-transform: uppercase; }
.empty { color: var(--muted); }
.banner {
  border-radius: 8px;
  padding: 10px 14px;
  font-size: 0.9rem;
}
.banner-error { background: #3a1e26; color: #ff8a8a; border: 1px solid #5e2f1e; }
.banner-info { background: var(--panel); color: var(--muted); border: 1px solid var(--border); }
</style>
