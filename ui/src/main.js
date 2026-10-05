import { createApp } from 'vue';
import { createVuetify } from 'vuetify';
import { VAlert, VApp, VAppBar, VAutocomplete, VAvatar, VBtn, VCard, VCardText, VCardTitle, VCheckbox, VChip, VChipGroup, VCol, VContainer, VDataTable, VDivider, VFileInput, VIcon, VList, VListItem, VMain, VMenu, VRow, VSelect, VSwitch, VTab, VTable, VTabs, VTextField, VTextarea, VTooltip, VWindow, VWindowItem } from 'vuetify/components';
import { iconConfig } from './icons';
import { Ripple } from 'vuetify/directives';
import 'vuetify/styles';
import './styles/main.css';
import App from './App.vue';

const vuetify = createVuetify({
  components: { VAlert, VApp, VAppBar, VAutocomplete, VAvatar, VBtn, VCard, VCardText, VCardTitle, VCheckbox, VChip, VChipGroup, VCol, VContainer, VDataTable, VDivider, VFileInput, VIcon, VList, VListItem, VMain, VMenu, VRow, VSelect, VSwitch, VTab, VTable, VTabs, VTextField, VTextarea, VTooltip, VWindow, VWindowItem },
  directives: { Ripple },
  icons: iconConfig,
  theme: {
    defaultTheme: 'expandLight',
    themes: {
      expandLight: {
        dark: false,
        colors: {
          primary: '#1C4E80',
          secondary: '#F18F01',
          accent: '#2E8B57',
          surface: '#FFFFFF',
          background: '#F6F4F0',
          error: '#B00020',
          info: '#1565C0',
          success: '#2E7D32',
          warning: '#F9A825'
        }
      }
    }
  }
});

createApp(App).use(vuetify).mount('#app');
