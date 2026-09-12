import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router/index.js'
import { bootstrap, wireAuthFailure } from './stores/auth.js'

// expired-session signal from the HTTP client → forced re-login
wireAuthFailure(router)
// fire-and-forget: reconciles a cached token pair with /me in the background
bootstrap()

createApp(App).use(router).mount('#app')
