<script setup>
/**
 * Finding detail modal — fetches the full record (evidence is only in the
 * detail view, not the list payload) and renders it with explicit loading
 * and error states. Closes on backdrop click, Esc, or the ✕ button.
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { api, ApiError } from '../api/client.js'
import SeverityBadge from './SeverityBadge.vue'

const props = defineProps({
  findingId: { type: Number, default: null },
})
const emit = defineEmits(['close'])

const detail = ref(null)
const loading = ref(false)
const errorMsg = ref('')

async function load() {
  if (props.findingId == null) return
  loading.value = true
  errorMsg.value = ''
  detail.value = null
  try {
    detail.value = await api.getFinding(props.findingId)
  } catch (err) {
    errorMsg.value = err instanceof ApiError ? err.message : 'Failed to load finding'
  } finally {
    loading.value = false
  }
}

function onKeydown(event) {
  if (event.key === 'Escape') emit('close')
}

watch(() => props.findingId, load)
onMounted(load)
onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="backdrop" @click.self="emit('close')">
    <div class="modal" role="dialog" aria-modal="true" aria-label="Finding detail">
      <header>
        <h3 v-if="detail">
          <SeverityBadge :severity="detail.severity" />
          {{ detail.title }}
        </h3>
        <h3 v-else>Finding</h3>
        <button class="close" type="button" aria-label="Close" @click="emit('close')">✕</button>
      </header>

      <p v-if="loading" class="state" role="status">Loading finding…</p>
      <p v-else-if="errorMsg" class="state state-error" role="alert">{{ errorMsg }}</p>

      <template v-if="detail">
        <dl class="fields">
          <div><dt>Source</dt><dd><code>{{ detail.source }}</code> · rule <code>{{ detail.rule_id }}</code></dd></div>
          <div><dt>URL</dt><dd class="wrap">{{ detail.url }}</dd></div>
          <div><dt>Parameter</dt><dd>{{ detail.param ?? '—' }} <span v-if="detail.method" class="muted">({{ detail.method }})</span></dd></div>
          <div><dt>OWASP</dt><dd>{{ detail.owasp_category ?? '—' }}</dd></div>
          <div><dt>CWE</dt><dd>{{ detail.cwe_id ?? '—' }}</dd></div>
          <div>
            <dt>ML severity</dt>
            <dd>
              {{ detail.severity }}
              <span v-if="detail.severity_confidence != null" class="muted">
                · confidence {{ (detail.severity_confidence * 100).toFixed(0) }}%
              </span>
            </dd>
          </div>
          <div class="span"><dt>Description</dt><dd>{{ detail.description ?? '—' }}</dd></div>
        </dl>

        <div class="evidence">
          <h4>Evidence <span class="muted">(redacted)</span></h4>
          <pre v-if="Object.keys(detail.evidence ?? {}).length">{{ JSON.stringify(detail.evidence, null, 2) }}</pre>
          <p v-else class="muted">No evidence retained for this finding.</p>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.backdrop {
  position: fixed;
  inset: 0;
  background: rgba(5, 8, 15, 0.72);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  z-index: 50;
}
.modal {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 12px;
  width: min(720px, 100%);
  max-height: 86vh;
  overflow-y: auto;
  padding: 18px 22px 22px;
}
header { display: flex; align-items: flex-start; gap: 12px; }
h3 { margin: 0; font-size: 1.02rem; display: flex; align-items: center; gap: 10px; flex: 1; }
.close {
  background: none;
  border: 1px solid var(--border);
  border-radius: 6px;
  color: var(--muted);
  cursor: pointer;
  padding: 2px 9px;
}
.fields { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 10px; margin: 16px 0; }
.fields .span { grid-column: 1 / -1; }
.fields dt { color: var(--muted); font-size: 0.7rem; text-transform: uppercase; margin-bottom: 2px; }
.fields dd { margin: 0; font-size: 0.9rem; }
.wrap { word-break: break-all; }
.muted { color: var(--muted); font-weight: 400; font-size: 0.85em; }
.evidence h4 { margin: 0 0 8px; font-size: 0.85rem; }
.evidence pre {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 12px;
  font-size: 0.78rem;
  overflow-x: auto;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}
.state { color: var(--muted); }
.state-error { color: #ff8a8a; }
</style>
