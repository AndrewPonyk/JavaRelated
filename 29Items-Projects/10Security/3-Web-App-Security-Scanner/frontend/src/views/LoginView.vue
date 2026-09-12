<script setup>
/**
 * Login / registration. Client-side validation mirrors the backend schema
 * (email shape, password ≥ 10 chars) so obvious mistakes never leave the
 * browser; server-side validation errors are still surfaced verbatim.
 */
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login, register } from '../stores/auth.js'
import { ApiError } from '../api/client.js'

const route = useRoute()
const router = useRouter()

const mode = ref('login') // 'login' | 'register'
const submitting = ref(false)
const errorMsg = ref('')
const notice = ref('')

const form = reactive({
  email: '',
  password: '',
  confirm: '',
  full_name: '',
})

const EMAIL_RE = /^[^@\s]+@[^@\s]+\.[^@\s]+$/

function validate() {
  if (!EMAIL_RE.test(form.email)) return 'Enter a valid email address.'
  if (mode.value === 'login') {
    if (!form.password) return 'Enter your password.'
  } else {
    if (form.full_name.trim().length < 2) return 'Enter your full name.'
    if (form.password.length < 10) return 'Password must be at least 10 characters.'
    if (form.password !== form.confirm) return 'Passwords do not match.'
  }
  return ''
}

function switchMode(next) {
  mode.value = next
  errorMsg.value = ''
  notice.value = ''
}

async function submit() {
  errorMsg.value = ''
  notice.value = ''
  const invalid = validate()
  if (invalid) {
    errorMsg.value = invalid
    return
  }

  submitting.value = true
  try {
    if (mode.value === 'login') {
      await login(form.email, form.password)
    } else {
      await register({
        email: form.email,
        password: form.password,
        full_name: form.full_name.trim() || null,
      })
      notice.value = 'Account created — welcome.'
    }
    router.push(typeof route.query.redirect === 'string' ? route.query.redirect : '/')
  } catch (err) {
    errorMsg.value = err instanceof ApiError ? err.message : 'Unexpected error'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="panel login-panel">
    <h2>{{ mode === 'login' ? 'Sign in' : 'Create an account' }}</h2>
    <p class="hint">
      {{ mode === 'login'
        ? 'Use your scanner account, or an API key issued under your user.'
        : 'New accounts start with viewer access — an admin can promote you.' }}
    </p>

    <form @submit.prevent="submit" novalidate>
      <label>
        Email
        <input
          v-model="form.email"
          type="email"
          autocomplete="username"
          placeholder="you@example.com"
          :disabled="submitting"
          required
        />
      </label>

      <label v-if="mode === 'register'">
        Full name
        <input
          v-model="form.full_name"
          type="text"
          autocomplete="name"
          placeholder="Ada Lovelace"
          :disabled="submitting"
        />
      </label>

      <label>
        Password
        <input
          v-model="form.password"
          type="password"
          :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
          placeholder="At least 10 characters"
          :disabled="submitting"
          required
        />
      </label>

      <label v-if="mode === 'register'">
        Confirm password
        <input v-model="form.confirm" type="password" autocomplete="new-password" :disabled="submitting" />
      </label>

      <button type="submit" :disabled="submitting">
        {{ submitting
          ? 'Working…'
          : mode === 'login' ? 'Sign in' : 'Create account & sign in' }}
      </button>
    </form>

    <p v-if="submitting" class="state state-loading" role="status">Authenticating…</p>
    <p v-else-if="errorMsg" class="state state-error" role="alert">{{ errorMsg }}</p>
    <p v-else-if="notice" class="state state-ok" role="status">{{ notice }}</p>

    <p class="switch">
      {{ mode === 'login' ? 'No account yet?' : 'Already registered?' }}
      <button type="button" class="link" @click="switchMode(mode === 'login' ? 'register' : 'login')">
        {{ mode === 'login' ? 'Register' : 'Sign in instead' }}
      </button>
    </p>
  </section>
</template>

<style scoped>
.login-panel { max-width: 420px; margin: 8vh auto 0; }
.panel {
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 20px 24px 24px;
}
h2 { margin: 0 0 4px; font-size: 1.15rem; }
.hint { color: var(--muted); font-size: 0.85rem; margin: 0 0 16px; }
form { display: flex; flex-direction: column; gap: 12px; }
label { display: flex; flex-direction: column; gap: 4px; font-size: 0.85rem; color: var(--muted); }
input {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 6px;
  color: var(--text);
  padding: 9px 10px;
  font-size: 0.95rem;
}
input:focus { outline: 1px solid var(--accent); }
button[type='submit'] {
  background: var(--accent);
  border: 0;
  border-radius: 6px;
  color: #fff;
  font-weight: 600;
  padding: 10px 22px;
  cursor: pointer;
  margin-top: 4px;
}
button[type='submit']:disabled { opacity: 0.6; cursor: wait; }
.state { margin: 14px 0 0; font-size: 0.9rem; }
.state-error { color: #ff8a8a; }
.state-ok { color: #7ee2a8; }
.state-loading { color: var(--muted); }
.switch { margin: 18px 0 0; font-size: 0.85rem; color: var(--muted); }
.link { background: none; border: 0; color: var(--accent); cursor: pointer; padding: 0; font-size: inherit; }
</style>
