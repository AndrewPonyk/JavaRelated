/**
 * Tests: components — forms (validation + states), findings browser
 * (data/empty/error + detail modal), severity badge, dashboard smoke.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ScanForm from '../src/components/ScanForm.vue'
import SeverityBadge from '../src/components/SeverityBadge.vue'
import FindingsTable from '../src/components/FindingsTable.vue'
import LoginView from '../src/views/LoginView.vue'
import DashboardView from '../src/views/DashboardView.vue'

const jsonResponse = (status, body) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

/** Minimal router so router-link-using views mount. */
function testRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'dashboard', component: { template: '<div/>' } },
      { path: '/login', name: 'login', component: { template: '<div/>' } },
      { path: '/scans/:id', name: 'scan-detail', component: { template: '<div/>' } },
    ],
  })
}

beforeEach(() => {
  localStorage.clear()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('SeverityBadge', () => {
  it('maps severity to its css class', () => {
    const badge = mount(SeverityBadge, { props: { severity: 'critical' } })
    expect(badge.classes()).toContain('sev-critical')
    expect(badge.text()).toBe('critical')
  })
})

describe('ScanForm', () => {
  it('rejects an invalid URL client-side without touching the API', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mount(ScanForm)
    await wrapper.find('input[type="url"]').setValue('not-a-url')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.text()).toContain('valid http(s) target URL')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('submits a valid target and emits scan-created', async () => {
    const scan = { id: 12, target_url: 'https://app.example.com', profile: 'fast', status: 'pending' }
    const fetchMock = vi.fn(() => jsonResponse(201, scan))
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mount(ScanForm)
    await wrapper.find('input[type="url"]').setValue('https://app.example.com')
    await wrapper.find('select').setValue('fast')
    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() => expect(wrapper.emitted('scan-created')).toHaveLength(1))

    const body = JSON.parse(fetchMock.mock.calls[0][1].body)
    expect(body).toEqual({ target_url: 'https://app.example.com', profile: 'fast' })
  })

  it('surfaces API errors from the normalized envelope', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => jsonResponse(403, { error: { code: 'http_403', message: 'viewer role too low' } })),
    )

    const wrapper = mount(ScanForm)
    await wrapper.find('input[type="url"]').setValue('https://app.example.com')
    await wrapper.find('form').trigger('submit')

    await vi.waitFor(() => expect(wrapper.text()).toContain('viewer role too low'))
  })
})

describe('LoginView', () => {
  it('validates the register form client-side', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mount(LoginView, { global: { plugins: [testRouter()] } })
    await wrapper.findAll('button.link')[0].trigger('click') // switch to register

    await wrapper.find('input[type="email"]').setValue('ok@test.dev')
    await wrapper.find('input[type="text"]').setValue('New User')
    await wrapper.findAll('input[type="password"]')[0].setValue('short')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.text()).toContain('at least 10 characters')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('shows the server message on failed login', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => jsonResponse(401, { error: { code: 'http_401', message: 'Invalid email or password' } })),
    )

    const wrapper = mount(LoginView, { global: { plugins: [testRouter()] } })
    await wrapper.find('input[type="email"]').setValue('x@test.dev')
    await wrapper.find('input[type="password"]').setValue('whatever-123')
    await wrapper.find('form').trigger('submit')

    await vi.waitFor(() => expect(wrapper.text()).toContain('Invalid email or password'))
  })
})

describe('FindingsTable', () => {
  const PAGE = {
    items: [
      {
        id: 3, scan_id: 1, source: 'zap', rule_id: '40018', title: 'SQL Injection',
        url: 'http://t/items?id=1', severity: 'high', severity_confidence: 0.82,
        owasp_category: 'A03:2021', cwe_id: 'CWE-89', description: null,
      },
    ],
    total: 1, page: 1, page_size: 25,
  }

  it('renders rows from the FindingsPage contract', async () => {
    vi.stubGlobal('fetch', vi.fn(() => jsonResponse(200, PAGE)))

    const wrapper = mount(FindingsTable)
    await vi.waitFor(() => expect(wrapper.text()).toContain('SQL Injection'))

    expect(wrapper.text()).toContain('A03:2021')
    expect(wrapper.text()).toContain('ML 82%')
    expect(wrapper.find('.sev-high').exists()).toBe(true)
  })

  it('shows the empty state for zero findings', async () => {
    vi.stubGlobal('fetch', vi.fn(() => jsonResponse(200, { items: [], total: 0, page: 1, page_size: 25 })))

    const wrapper = mount(FindingsTable)
    await vi.waitFor(() => expect(wrapper.text()).toContain('No findings'))
  })

  it('shows the error state on API failure', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => jsonResponse(500, { error: { code: 'http_500', message: 'database unavailable' } })),
    )

    const wrapper = mount(FindingsTable)
    await vi.waitFor(() => expect(wrapper.text()).toContain('database unavailable'))
  })

  it('opens the detail modal with evidence on row click', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((url) =>
        String(url).includes('/findings/3')
          ? jsonResponse(200, { ...PAGE.items[0], evidence: { excerpt: 'OR 1=1' } })
          : jsonResponse(200, PAGE),
      ),
    )

    const wrapper = mount(FindingsTable)
    await vi.waitFor(() => expect(wrapper.find('tbody tr').exists()).toBe(true))

    await wrapper.find('tbody tr').trigger('click')
    await vi.waitFor(() => expect(wrapper.find('[role="dialog"]').exists()).toBe(true))
    await vi.waitFor(() => expect(wrapper.text()).toContain('OR 1=1'))

    // closing returns to the plain table
    await wrapper.find('.close').trigger('click')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })
})

describe('DashboardView smoke', () => {
  it('renders stats and the viewer notice without a scan role', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((url) => {
        const target = String(url)
        if (target.includes('/findings/stats')) {
          return jsonResponse(200, [{ severity: 'high', count: 2 }])
        }
        if (target.includes('/findings')) {
          return jsonResponse(200, { items: [], total: 0, page: 1, page_size: 25 })
        }
        // scans list
        return jsonResponse(200, [
          { id: 5, target_url: 'http://t/', profile: 'fast', status: 'completed' },
        ])
      }),
    )

    const wrapper = mount(DashboardView, { global: { plugins: [testRouter()] } })
    await vi.waitFor(() => expect(wrapper.text()).toContain('Severity overview'))

    // no logged-in user → viewer notice instead of the scan form
    expect(wrapper.text()).toContain('viewer access')
    expect(wrapper.findComponent(ScanForm).exists()).toBe(false)
    expect(wrapper.text()).toContain('2') // stat count rendered
  })
})
