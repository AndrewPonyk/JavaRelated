<script setup>
/**
 * Scan-target allowlist management (admin). When the allowlist is empty the
 * backend runs in bootstrap mode (any in-scope public target allowed); once
 * at least one pattern exists, only matching hosts/CIDRs are scannable —
 * this view is where operators curate that set.
 *
 * Client-side validation mirrors the backend TargetCreate schema: bare
 * hostname or CIDR, no scheme/path.
 */
import { onMounted, reactive, ref } from 'vue'
import { api, ApiError } from '../api/client.js'

const targets = ref([])
const loading = ref(false)
const errorMsg = ref('')
const rowBusy = ref(0)
const formMsg = ref('')
const submitting = ref(false)

const form = reactive({ host_pattern: '', description: '' })

const HOSTNAME_RE = /^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$/i
const CIDR_RE = /^\d{1,3}(\.\d{1,3}){3}\/\d{1,2}$/
const SINGLE_IP_RE = /^\d{1,3}(\.\d{1,3}){3}$/

function validatePattern(value) {
  const v = value.trim().toLowerCase().replace(/\.$/, '')
  if (!v) return { error: 'Enter a hostname or CIDR.' }
  if (CIDR_RE.test(v) || SINGLE_IP_RE.test(v)) return { value: v }
  if (v.includes('://') || v.includes('/') || v.includes(' ')) {
    return { error: 'Pattern must be a bare hostname or CIDR — no scheme or path.' }
  }
  if (!HOSTNAME_RE.test(v)) {
    return { error: 'Not a valid hostname or CIDR (e.g. app.example.com, 10.0.0.0/8).' }
  }
  return { value: v }
}

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    targets.value = await api.listTargets()
  } catch (err) {
    errorMsg.value = err instanceof ApiError ? err.message : 'Failed to load targets'
  } finally {
    loading.value = false
  }
}

async function submit() {
  formMsg.value = ''
  const check = validatePattern(form.host_pattern)
  if (check.error) {
    formMsg.value = check.error
    return
  }

  submitting.value = true
  try {
    await api.createTarget({
      host_pattern: check.value,
      description: form.description.trim() || null,
    })
    form.host_pattern = ''
    form.description = ''
    await load()
  } catch (err) {
    formMsg.value = err instanceof ApiError ? err.message : 'Failed to add target'
  } finally {
    submitting.value = false
  }
}

async function verify(target) {
  rowBusy.value = target.id
  try {
    const updated = await api.verifyTarget(target.id)
    Object.assign(target, updated)
  } catch (err) {
    errorMsg.value = err instanceof ApiError ? err.message : 'Verification failed'
  } finally {
    rowBusy.value = 0
  }
}

async function remove(target) {
  if (!window.confirm(`Remove allowlist pattern "${target.host_pattern}"?`)) return
  rowBusy.value = target.id
  try {
    await api.deleteTarget(target.id)
    targets.value = targets.value.filter((t) => t.id !== target.id)
  } catch (err) {
    errorMsg.value = err instanceof ApiError ? err.message : 'Delete failed'
  } finally {
    rowBusy.value = 0
  }
}

function fmt(dt) {
  return dt ? new Date(dt).toLocaleString() : '—'
}

onMounted(load)
</script>

<template>
  <p v-if="errorMsg" class="banner banner-error" role="alert">{{ errorMsg }}</p>

  <section class="panel">
    <h2>Scan-target allowlist</h2>
    <p class="hint">
      Empty allowlist = bootstrap mode (any in-scope public target may be scanned).
      Once a pattern is added, only matching hosts and CIDR ranges are scannable.
    </p>

    <form class="add-form" @submit.prevent="submit" novalidate>
      <input
        v-model="form.host_pattern"
        type="text"
        placeholder="app.example.com or 10.0.0.0/8"
        aria-label="Host pattern"
        :disabled="submitting"
      />
      <input
        v-model="form.description"
        type="text"
        placeholder="Optional description"
        aria-label="Description"
        :disabled="submitting"
      />
      <button type="submit" :disabled="submitting">
        {{ submitting ? 'Adding…' : 'Add' }}
      </button>
    </form>
    <p v-if="formMsg" class="state state-error" role="alert">{{ formMsg }}</p>

    <p v-if="loading" class="state">Loading targets…</p>
    <p v-else-if="targets.length === 0" class="state state-empty">
      No allowlist patterns — bootstrap mode is active.
    </p>

    <div v-else class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Pattern</th><th>Description</th><th>Verified</th><th>Added</th><th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in targets" :key="t.id">
            <td><code>{{ t.host_pattern }}</code></td>
            <td class="desc">{{ t.description ?? '—' }}</td>
            <td>
              <span :class="t.verified_at ? 'ok' : 'muted'">
                {{ t.verified_at ? fmt(t.verified_at) : 'not verified' }}
              </span>
            </td>
            <td class="muted">{{ fmt(t.created_at) }}</td>
            <td class="row-actions">
              <button type="button" :disabled="rowBusy === t.id" @click="verify(t)">Verify</button>
              <button type="button" class="danger" :disabled="rowBusy === t.id" @click="remove(t)">
                Delete
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.panel {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 16px 20px;
}
h2 { margin: 0 0 4px; font-size: 1.05rem; }
.hint { color: var(--muted); font-size: 0.85rem; margin: 0 0 14px; }
.add-form { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
.add-form input {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  color: var(--text);
  padding: 8px 10px;
}
.add-form input:first-child { flex: 2 1 240px; }
.add-form input:last-of-type { flex: 3 1 280px; }
.add-form button {
  background: var(--accent);
  border: 0;
  border-radius: 6px;
  color: #fff;
  font-weight: 600;
  padding: 8px 20px;
  cursor: pointer;
}
.add-form button:disabled { opacity: 0.6; cursor: wait; }
.state { color: var(--muted); }
.state-error { color: #ff8a8a; }
.table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 0.88rem; }
th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--border); }
th { color: var(--muted); font-weight: 500; text-transform: uppercase; font-size: 0.72rem; }
.desc { max-width: 280px; }
.muted { color: var(--muted); }
.ok { color: #7ee2a8; }
.row-actions { white-space: nowrap; }
.row-actions button {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  color: var(--text);
  padding: 4px 10px;
  cursor: pointer;
  font-size: 0.8rem;
}
.row-actions button:disabled { opacity: 0.5; cursor: wait; }
.row-actions .danger { border-color: #5e2f1e; color: #ff9d7a; }
.banner { border-radius: 8px; padding: 10px 14px; font-size: 0.9rem; }
.banner-error { background: #3a1e26; color: #ff8a8a; border: 1px solid #5e2f1e; }
</style>
