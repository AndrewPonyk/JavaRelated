<script setup>
defineProps({
  severity: { type: String, required: true },
})
</script>

<template>
  <span class="badge" :class="`sev-${severity}`">{{ severity }}</span>
</template>

<style scoped>
.badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.sev-info     { background: #2c3550; color: #aebbdd; }
.sev-low      { background: #27503c; color: #7ee2a8; }
.sev-medium   { background: #5a4a1e; color: #ffd76e; }
.sev-high     { background: #5e2f1e; color: #ff9d7a; }
.sev-critical { background: #5e1e2b; color: #ff7a9e; }
</style>
