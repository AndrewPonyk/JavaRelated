<script setup>
/**
 * App shell: top navigation + routed content. Navigation items are
 * role-aware (Targets / Users management are admin-only surfaces).
 */
import { useRouter } from 'vue-router'
import { authStore, isAdmin, logout } from './stores/auth.js'

const router = useRouter()

function onLogout() {
  logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <div class="app-title">
        <h1>🛡️ Web App Security Scanner</h1>
        <span class="app-sub">ZAP · SQLMap · XSS engine · ML severity</span>
      </div>

      <nav v-if="authStore.user" class="app-nav" aria-label="Main">
        <RouterLink :to="{ name: 'dashboard' }">Dashboard</RouterLink>
        <RouterLink v-if="isAdmin" :to="{ name: 'targets' }">Targets</RouterLink>
        <RouterLink v-if="isAdmin" :to="{ name: 'admin-users' }">Users</RouterLink>
      </nav>

      <div v-if="authStore.user" class="user-box">
        <span class="user-email" :title="authStore.user.email">{{ authStore.user.email }}</span>
        <span class="role-chip" :class="`role-${authStore.user.role}`">{{ authStore.user.role }}</span>
        <button class="logout" type="button" @click="onLogout">Log out</button>
      </div>
    </header>

    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>

<style>
:root {
  --bg: #0f1420;
  --panel: #1a2233;
  --border: #2a3550;
  --text: #e6ebf5;
  --muted: #8b96ad;
  --accent: #4f8cff;
}
* { box-sizing: border-box; }
body {
  margin: 0;
  font-family: system-ui, -apple-system, 'Segoe UI', sans-serif;
  background: var(--bg);
  color: var(--text);
}
.app-shell { max-width: 1100px; margin: 0 auto; padding: 0 16px 48px; }
.app-header {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  padding: 20px 0 14px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 20px;
}
.app-title h1 { font-size: 1.25rem; margin: 0; }
.app-sub { color: var(--muted); font-size: 0.8rem; }
.app-nav { display: flex; gap: 4px; flex-wrap: wrap; }
.app-nav a {
  color: var(--muted);
  text-decoration: none;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 0.92rem;
}
.app-nav a.router-link-active { color: var(--text); background: var(--panel); }
.user-box { display: flex; align-items: center; gap: 8px; margin-left: auto; }
.user-email {
  color: var(--muted);
  font-size: 0.85rem;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.role-chip {
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--panel);
  border: 1px solid var(--border);
  color: var(--muted);
}
.role-chip.role-admin { color: #ff9d7a; border-color: #5e2f1e; }
.role-chip.role-scanner { color: #7ee2a8; border-color: #27503c; }
.logout {
  background: none;
  border: 1px solid var(--border);
  border-radius: 6px;
  color: var(--text);
  padding: 5px 12px;
  cursor: pointer;
  font-size: 0.85rem;
}
.logout:hover { border-color: var(--accent); }
.app-main { display: flex; flex-direction: column; gap: 20px; }
@media (max-width: 640px) {
  .user-box { margin-left: 0; width: 100%; justify-content: space-between; }
}
</style>
