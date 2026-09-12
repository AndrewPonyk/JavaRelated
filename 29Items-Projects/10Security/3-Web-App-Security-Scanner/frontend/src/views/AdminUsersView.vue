<script setup>
/**
 * User administration (admin): role management and account enable/disable.
 * The backend rejects self-lockout and last-active-admin demotion (409) —
 * those messages surface here verbatim and the row reverts.
 */
import { onMounted, ref } from 'vue'
import { api, ApiError } from '../api/client.js'
import { authStore } from '../stores/auth.js'

const users = ref([])
const loading = ref(false)
const errorMsg = ref('')
const rowBusy = ref(0)

const ROLES = ['viewer', 'scanner', 'admin']

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    users.value = await api.listUsers()
  } catch (err) {
    errorMsg.value = err instanceof ApiError ? err.message : 'Failed to load users'
  } finally {
    loading.value = false
  }
}

async function patch(user, payload, revert) {
  rowBusy.value = user.id
  errorMsg.value = ''
  try {
    Object.assign(user, await api.updateUser(user.id, payload))
  } catch (err) {
    Object.assign(user, revert) // optimistic UI rolled back
    errorMsg.value = err instanceof ApiError ? err.message : 'Update failed'
  } finally {
    rowBusy.value = 0
  }
}

function changeRole(user, event) {
  const role = event.target.value
  const revert = { role: user.role }
  user.role = role
  patch(user, { role }, revert)
}

function toggleActive(user) {
  const is_active = !user.is_active
  patch(user, { is_active }, { is_active: user.is_active })
}

function fmt(dt) {
  return dt ? new Date(dt).toLocaleDateString() : '—'
}

onMounted(load)
</script>

<template>
  <p v-if="errorMsg" class="banner banner-error" role="alert">{{ errorMsg }}</p>

  <section class="panel">
    <h2>Users</h2>
    <p class="hint">Roles: viewer (read-only) &lt; scanner (launch scans) &lt; admin (manage).</p>

    <p v-if="loading" class="state">Loading users…</p>

    <div v-else class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Email</th><th>Name</th><th>Role</th><th>Active</th><th>Joined</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in users" :key="u.id" :class="{ me: u.id === authStore.user?.id }">
            <td class="email">{{ u.email }} <span v-if="u.id === authStore.user?.id" class="you">(you)</span></td>
            <td>{{ u.full_name ?? '—' }}</td>
            <td>
              <select
                :value="u.role"
                :disabled="rowBusy === u.id || u.id === authStore.user?.id"
                :aria-label="`Role for ${u.email}`"
                @change="changeRole(u, $event)"
              >
                <option v-for="r in ROLES" :key="r" :value="r">{{ r }}</option>
              </select>
            </td>
            <td>
              <button
                type="button"
                class="toggle"
                :class="{ on: u.is_active }"
                :disabled="rowBusy === u.id || u.id === authStore.user?.id"
                @click="toggleActive(u)"
              >
                {{ u.is_active ? 'active' : 'disabled' }}
              </button>
            </td>
            <td class="muted">{{ fmt(u.created_at) }}</td>
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
.state { color: var(--muted); }
.table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 0.88rem; }
th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--border); }
th { color: var(--muted); font-weight: 500; text-transform: uppercase; font-size: 0.72rem; }
tr.me td { background: rgba(79, 140, 255, 0.06); }
.email { word-break: break-all; }
.you { color: var(--accent); font-size: 0.75rem; }
.muted { color: var(--muted); }
select {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  color: var(--text);
  padding: 4px 8px;
}
select:disabled { opacity: 0.5; }
.toggle {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 999px;
  color: var(--muted);
  padding: 3px 12px;
  cursor: pointer;
  font-size: 0.8rem;
}
.toggle.on { color: #7ee2a8; border-color: #27503c; }
.toggle:disabled { opacity: 0.5; cursor: default; }
.banner { border-radius: 8px; padding: 10px 14px; font-size: 0.9rem; }
.banner-error { background: #3a1e26; color: #ff8a8a; border: 1px solid #5e2f1e; }
</style>
