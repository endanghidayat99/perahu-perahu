import { createApp } from 'vue'
import { createVuetify } from 'vuetify'
import '@mdi/font/css/materialdesignicons.css'
import 'vuetify/styles'
import './style.css'
import App from './App.vue'

const vuetify = createVuetify({
  theme: {
    defaultTheme: 'perahu',
    themes: {
      perahu: {
        dark: false,
        colors: {
          primary: '#166534',
          secondary: '#b45309',
          surface: '#ffffff',
          background: '#f0fdf4',
          error: '#b91c1c',
        },
      },
    },
  },
  defaults: {
    VBtn: { rounded: 'lg' },
    VTextField: { variant: 'outlined', density: 'comfortable' },
  },
})

createApp(App).use(vuetify).mount('#app')
