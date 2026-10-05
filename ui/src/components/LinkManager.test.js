import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import LinkManager from './LinkManager.vue';

describe('relation editor', () => {
  it('validates required relation attributes before a mutation', async () => {
    const state = { modelDetails: { linkTypes: [{ name: 'Knows', attributes: [{ name: 'label', required: true }] }] }, dataLinks: [{ id: 7, type: 'Knows', fromKey: '1', toKey: '2', attributes: [] }], canUpdateCurrentModelData: true, canDeleteCurrentModelData: true, mutateLink: vi.fn() };
    const wrapper = mount(LinkManager, { props: { state }, global: { stubs: { VCard: { template: '<div><slot /></div>' }, VCardText: { template: '<div><slot /></div>' }, VSelect: { template: '<button @click="$emit(\'update:modelValue\', 7)">Select</button>' }, VTextField: { props: ['modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' }, VBtn: { template: '<button><slot /></button>' } } } });
    await wrapper.find('button').trigger('click');
    await wrapper.find('input').setValue('');
    await wrapper.findAll('button')[1].trigger('click');
    expect(state.mutateLink).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('obligatoires');
    await wrapper.find('input').setValue('Friend');
    await wrapper.findAll('button')[1].trigger('click');
    expect(state.mutateLink).toHaveBeenCalledWith(state.dataLinks[0], [{ key: 'label', value: 'Friend' }]);
  });
});
