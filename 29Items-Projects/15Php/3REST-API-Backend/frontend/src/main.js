import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from '@/App.vue'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import '@/style.css'

const app = createApp(App)

app.use(createPinia())

// Restore any persisted session (token) before the first route resolves.
useAuthStore().init()

app.use(router)
app.mount('#app')
