/**
 * Routes + navigation guards.
 *
 * - public routes (login) are always reachable
 * - everything else requires a session (token pair or cached user)
 * - meta.admin additionally requires role === 'admin'
 * Unauthenticated users are parked on /login with a `redirect` query so the
 * post-login landing restores their destination.
 */

import { createRouter, createWebHistory } from 'vue-router'
import { isAdmin, isAuthenticated } from '../stores/auth.js'
import DashboardView from '../views/DashboardView.vue'
import LoginView from '../views/LoginView.vue'
import ScanDetailView from '../views/ScanDetailView.vue'
import TargetsView from '../views/TargetsView.vue'
import AdminUsersView from '../views/AdminUsersView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/', name: 'dashboard', component: DashboardView },
    { path: '/scans/:id(\\d+)', name: 'scan-detail', component: ScanDetailView, props: true },
    { path: '/targets', name: 'targets', component: TargetsView, meta: { admin: true } },
    { path: '/admin/users', name: 'admin-users', component: AdminUsersView, meta: { admin: true } },
    { path: '/:pathMatch(.*)*', name: 'not-found', redirect: { name: 'dashboard' } },
  ],
})

router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!isAuthenticated()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.admin && !isAdmin()) {
    return { name: 'dashboard' } // authenticated but insufficient role
  }
  return true
})
