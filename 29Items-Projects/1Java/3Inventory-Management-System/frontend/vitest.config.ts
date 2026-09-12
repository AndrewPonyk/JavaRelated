import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    include: ['src/**/*.spec.ts'],
    environment: 'happy-dom',
    setupFiles: ['./src/test/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      include: [
        'src/api/client.ts',
        'src/composables/offlineQueue.ts',
        'src/composables/useAuth.ts',
        'src/components/inventory/ItemForm.vue',
        'src/components/inventory/InventoryTable.vue',
      ],
      thresholds: { lines: 80, functions: 80, statements: 80, branches: 80 },
    },
  },
})
