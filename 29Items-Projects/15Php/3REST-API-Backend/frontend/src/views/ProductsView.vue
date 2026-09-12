<script setup>
import { onMounted, reactive, ref } from 'vue'
import api from '@/services/api'
import { useProducts } from '@/composables/useProducts'
import ProductList from '@/components/ProductList.vue'

const { products, meta, loading, error, fetchProducts } = useProducts()

const search = ref('')
const busyId = ref(null)
const message = ref('')

// ── Create-product form ───────────────────────────────────────────────────
const showForm = ref(false)
const creating = ref(false)
const formError = ref('')
const formErrors = reactive({})
const form = reactive({
  name: '',
  price: '',
  currency: 'USD',
  category: '',
  stock: '',
})

function load() {
  fetchProducts({ q: search.value || undefined })
}

onMounted(load)

function flash(text) {
  message.value = text
  setTimeout(() => (message.value = ''), 3000)
}

function validateForm() {
  Object.keys(formErrors).forEach((k) => delete formErrors[k])
  if (!form.name.trim()) formErrors.name = 'Name is required.'
  if (!(Number(form.price) > 0)) formErrors.price = 'Price must be greater than 0.'
  if (!form.category.trim()) formErrors.category = 'Category is required.'
  if (!(Number(form.stock) >= 0)) formErrors.stock = 'Stock must be 0 or more.'
  return Object.keys(formErrors).length === 0
}

async function createProduct() {
  formError.value = ''
  if (!validateForm()) return

  creating.value = true
  try {
    await api.post('/products', {
      name: form.name,
      price_cents: Math.round(Number(form.price) * 100),
      currency: form.currency,
      category: form.category,
      stock: Number(form.stock),
    })
    Object.assign(form, { name: '', price: '', currency: 'USD', category: '', stock: '' })
    showForm.value = false
    flash('Product created.')
    load()
  } catch (e) {
    if (e.errors) {
      Object.entries(e.errors).forEach(([field, messages]) => {
        formErrors[field] = messages[0]
      })
    }
    formError.value = e.message ?? 'Could not create product.'
  } finally {
    creating.value = false
  }
}

async function buy(product) {
  busyId.value = product.id
  try {
    await api.post('/orders', { items: [{ product_id: product.id, quantity: 1 }] })
    flash(`Ordered “${product.name}”.`)
    load() // reflect decremented stock
  } catch (e) {
    formError.value = e.message ?? 'Could not place order.'
  } finally {
    busyId.value = null
  }
}
</script>

<template>
  <section>
    <div class="toolbar">
      <h2>Products</h2>
      <div class="row">
        <form class="row" @submit.prevent="load">
          <input v-model="search" type="search" placeholder="Search…" aria-label="Search products" />
          <button class="btn btn--ghost" type="submit">Search</button>
        </form>
        <button class="btn" @click="showForm = !showForm">
          {{ showForm ? 'Close' : '+ New product' }}
        </button>
      </div>
    </div>

    <p v-if="message" class="alert alert--success">{{ message }}</p>

    <!-- Create form -->
    <form v-if="showForm" class="card" style="margin-bottom: 1.5rem" @submit.prevent="createProduct">
      <p v-if="formError" class="alert alert--error">{{ formError }}</p>
      <div class="field">
        <label for="p-name">Name</label>
        <input id="p-name" v-model="form.name" type="text" />
        <p v-if="formErrors.name" class="field__error">{{ formErrors.name }}</p>
      </div>
      <div class="row">
        <div class="field" style="flex: 1">
          <label for="p-price">Price (USD)</label>
          <input id="p-price" v-model="form.price" type="number" step="0.01" min="0" />
          <p v-if="formErrors.price" class="field__error">{{ formErrors.price }}</p>
        </div>
        <div class="field" style="flex: 1">
          <label for="p-stock">Stock</label>
          <input id="p-stock" v-model="form.stock" type="number" min="0" />
          <p v-if="formErrors.stock" class="field__error">{{ formErrors.stock }}</p>
        </div>
      </div>
      <div class="field">
        <label for="p-category">Category</label>
        <input id="p-category" v-model="form.category" type="text" placeholder="e.g. electronics" />
        <p v-if="formErrors.category" class="field__error">{{ formErrors.category }}</p>
      </div>
      <button class="btn" type="submit" :disabled="creating">
        {{ creating ? 'Saving…' : 'Create product' }}
      </button>
    </form>

    <ProductList
      :products="products"
      :loading="loading"
      :error="error"
      :busy-id="busyId"
      @buy="buy"
      @retry="load"
    />

    <p v-if="meta" class="muted" style="margin-top: 1rem">
      Page {{ meta.current_page }} of {{ meta.last_page }} · {{ meta.total }} products
    </p>
  </section>
</template>
