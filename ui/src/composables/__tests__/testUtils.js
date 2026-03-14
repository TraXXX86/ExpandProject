import { defineComponent, h } from 'vue';
import { mount } from '@vue/test-utils';
import { useAppState } from '../useAppState';

export function mountUseAppState() {
  let state;

  mount(defineComponent({
    name: 'UseAppStateHarness',
    setup() {
      state = useAppState();
      return () => h('div');
    }
  }));

  return state;
}

export function jsonResponse(body, { ok = true, status = 200 } = {}) {
  return {
    ok,
    status,
    json: async () => body
  };
}
