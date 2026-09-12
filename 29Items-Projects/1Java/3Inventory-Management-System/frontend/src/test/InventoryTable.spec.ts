import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import InventoryTable from '../components/inventory/InventoryTable.vue'
import { item } from './fixtures'

describe('InventoryTable', () => {
  it('renders stock and emits selection and edit actions', async () => {
    const wrapper = mount(InventoryTable, { props: { items: [item] } })
    expect(wrapper.text()).toContain('Widget')
    expect(wrapper.text()).toContain('10')
    await wrapper.find('tbody tr').trigger('click')
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('select')?.[0][0]).toEqual(item)
    expect(wrapper.emitted('edit')?.[0][0]).toEqual(item)
  })

  it('shows low-stock and inactive states', async () => {
    const wrapper = mount(InventoryTable, { props: { items: [{ ...item, lowStock: true }] } })
    expect(wrapper.text()).toContain('Low stock')
    await wrapper.setProps({ items: [{ ...item, active: false }] })
    expect(wrapper.text()).toContain('Inactive')
  })
})
