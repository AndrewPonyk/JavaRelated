<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const mode = ref('login') // 'login' | 'register'
const loading = ref(false)
const serverError = ref('')
const errors = reactive({})

const form = reactive({
  name: '',
  email: '',
  password: '',
  password_confirmation: '',
})

function validate() {
  Object.keys(errors).forEach((k) => delete errors[k])

  if (mode.value === 'register' && !form.name.trim()) {
    errors.name = 'Name is required.'
  }
  if (!/^\S+@\S+\.\S+$/.test(form.email)) {
    errors.email = 'A valid email is required.'
  }
  if (form.password.length < 8) {
    errors.password = 'Password must be at least 8 characters.'
  }
  if (mode.value === 'register' && form.password !== form.password_confirmation) {
    errors.password_confirmation = 'Passwords do not match.'
  }

  return Object.keys(errors).length === 0
}

async function submit() {
  serverError.value = ''
  if (!validate()) return

  loading.value = true
  try {
    if (mode.value === 'login') {
      await auth.login(form.email, form.password)
    } else {
      await auth.register({ ...form })
    }
    router.push(route.query.redirect ?? { name: 'products' })
  } catch (e) {
    // Map server-side 422 field errors, else show a general message.
    if (e.errors) {
      Object.entries(e.errors).forEach(([field, messages]) => {
        errors[field] = messages[0]
      })
    }
    serverError.value = e.message ?? 'Something went wrong.'
  } finally {
    loading.value = false
  }
}

function switchMode(next) {
  mode.value = next
  serverError.value = ''
  Object.keys(errors).forEach((k) => delete errors[k])
}
</script>

<template>
  <div class="card auth-card">
    <div class="tabs">
      <button :class="{ active: mode === 'login' }" @click="switchMode('login')">Log in</button>
      <button :class="{ active: mode === 'register' }" @click="switchMode('register')">Register</button>
    </div>

    <p v-if="serverError" class="alert alert--error">{{ serverError }}</p>

    <form novalidate @submit.prevent="submit">
      <div v-if="mode === 'register'" class="field">
        <label for="name">Name</label>
        <input id="name" v-model="form.name" type="text" autocomplete="name" />
        <p v-if="errors.name" class="field__error">{{ errors.name }}</p>
      </div>

      <div class="field">
        <label for="email">Email</label>
        <input id="email" v-model="form.email" type="email" autocomplete="email" />
        <p v-if="errors.email" class="field__error">{{ errors.email }}</p>
      </div>

      <div class="field">
        <label for="password">Password</label>
        <input id="password" v-model="form.password" type="password" autocomplete="current-password" />
        <p v-if="errors.password" class="field__error">{{ errors.password }}</p>
      </div>

      <div v-if="mode === 'register'" class="field">
        <label for="password_confirmation">Confirm password</label>
        <input
          id="password_confirmation"
          v-model="form.password_confirmation"
          type="password"
          autocomplete="new-password"
        />
        <p v-if="errors.password_confirmation" class="field__error">{{ errors.password_confirmation }}</p>
      </div>

      <button class="btn btn--block" type="submit" :disabled="loading">
        {{ loading ? 'Please wait…' : mode === 'login' ? 'Log in' : 'Create account' }}
      </button>
    </form>
  </div>
</template>
