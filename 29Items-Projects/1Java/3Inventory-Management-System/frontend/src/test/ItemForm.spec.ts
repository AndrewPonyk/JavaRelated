import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ItemForm from '../components/inventory/ItemForm.vue'
import { item, warehouse } from './fixtures'

describe('ItemForm', () => {
  it('validates required fields before submitting', async () => {
    const wrapper = mount(ItemForm, { props: { warehouses: [warehouse] } })
    await wrapper.find('input[required]').setValue('')
    await wrapper.find('form').trigger('submit')
    expect(wrapper.get('[role=alert]').text()).toContain('required')
    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('normalizes and submits a new item', async () => {
    const wrapper = mount(ItemForm, { props: { warehouses: [warehouse] } })
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue(' sku-2 ')
    await inputs[1].setValue('New widget')
    await wrapper.find('select').setValue(warehouse.id)
    await inputs[2].setValue('4006381333931')
    await inputs[3].setValue('ALIAS-1, ALIAS-2')
    await wrapper.find('form').trigger('submit')
    const submitted = wrapper.emitted('submit')?.[0][0] as {
      sku: string
      warehouseId: string
      aliases: unknown[]
    }
    expect(submitted).toMatchObject({ sku: 'SKU-2', warehouseId: warehouse.id })
    expect(submitted.aliases).toHaveLength(2)
  })

  it('populates edit values and emits cancellation', async () => {
    const wrapper = mount(ItemForm, { props: { warehouses: [warehouse], item } })
    expect(wrapper.text()).toContain('Edit WIDGET-1')
    await wrapper.find('button[type=button]').trigger('click')
    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('rejects negative stock boundaries', async () => {
    const wrapper = mount(ItemForm, { props: { warehouses: [warehouse] } })
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('SKU')
    await inputs[1].setValue('Name')
    await wrapper.find('select').setValue(warehouse.id)
    await inputs[2].setValue('CODE')
    await inputs[4].setValue('-1')
    await wrapper.find('form').trigger('submit')
    expect(wrapper.get('[role=alert]').text()).toContain('negative')
  })
})
