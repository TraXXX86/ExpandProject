import { createApp } from 'vue';
import { createVuetify } from 'vuetify';
import * as components from 'vuetify/components';
import * as directives from 'vuetify/directives';
import 'vuetify/styles';
import '@mdi/font/css/materialdesignicons.css';
import './styles/main.css';
import App from './App.vue';

const vuetify = createVuetify({
  components,
  directives,
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
