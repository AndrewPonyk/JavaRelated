import { ref } from 'vue'
import api from '@/services/api'

/**
 * Reusable products data-fetching composable.
 * Encapsulates loading / error / data state so components stay declarative.
 *
 * @returns {{
 *   products: import('vue').Ref<Array>,
 *   meta: import('vue').Ref<Object|null>,
 *   loading: import('vue').Ref<boolean>,
 *   error: import('vue').Ref<string|null>,
 *   fetchProducts: (params?: Object) => Promise<void>,
 * }}
 */
export function useProducts() {
  const products = ref([])
  const meta = ref(null)
  const loading = ref(false)
  const error = ref(null)

  async function fetchProducts(params = {}) {
    loading.value = true
    error.value = null

    try {
      const { data } = await api.get('/products', { params })
      products.value = data.data
      meta.value = data.meta
    } catch (e) {
      error.value = e.message ?? 'Failed to load products.'
      products.value = []
    } finally {
      loading.value = false
    }
  }

  return { products, meta, loading, error, fetchProducts }
}
