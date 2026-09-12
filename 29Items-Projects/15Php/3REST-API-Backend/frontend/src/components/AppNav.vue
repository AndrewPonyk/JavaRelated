<script setup>
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

async function onLogout() {
  await auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <header class="nav">
    <div class="nav__brand">🛍️ Marketplace</div>

    <nav v-if="auth.isAuthenticated" class="nav__links">
      <RouterLink to="/products">Products</RouterLink>
      <RouterLink to="/recommendations">For You</RouterLink>
      <RouterLink to="/orders">Orders</RouterLink>
    </nav>

    <div class="nav__user">
      <template v-if="auth.isAuthenticated">
        <span class="nav__email">{{ auth.user?.name ?? auth.user?.email }}</span>
        <button class="btn btn--ghost" @click="onLogout">Logout</button>
      </template>
    </div>
  </header>
</template>
