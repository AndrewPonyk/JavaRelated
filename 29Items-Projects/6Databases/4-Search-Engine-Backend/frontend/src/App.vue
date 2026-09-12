<script setup lang="ts">
import SearchView from "@/views/SearchView.vue";
</script>

<template>
  <header class="site-header">
    <h1>Product Search</h1>
  </header>
  <main>
    <SearchView />
  </main>
</template>

<style>
/* Minimal global reset — replace with a design system later. */
*,
*::before,
*::after {
  box-sizing: border-box;
}
body {
  margin: 0;
  font-family:
    system-ui,
    -apple-system,
    "Segoe UI",
    Roboto,
    sans-serif;
  color: #0f172a;
  background: #f8fafc;
}
.site-header {
  padding: 1rem 2rem;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
}
.site-header h1 {
  margin: 0;
  font-size: 1.25rem;
}
main {
  max-width: 72rem;
  margin: 0 auto;
  padding: 1.5rem 2rem 4rem;
}
</style>
