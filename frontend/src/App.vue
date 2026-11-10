<template>
  <v-app>
    <HomeView v-if="!store.session || !store.room" />
    <LobbyView v-else-if="store.room.phase === 'LOBBY'" />
    <GameView v-else />

    <v-snackbar v-model="showError" color="error" timeout="4000" location="top">
      {{ store.error?.message }}
      <span v-if="isRuleCode" class="ml-1 text-caption">({{ store.error.code }})</span>
    </v-snackbar>

    <v-snackbar :model-value="!store.connected" color="warning" timeout="-1" location="bottom">
      <v-progress-circular indeterminate size="16" width="2" class="mr-2" />
      Menyambung ke server…
    </v-snackbar>
  </v-app>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { store, connect } from './store'
import { music } from './music'
import HomeView from './views/HomeView.vue'
import LobbyView from './views/LobbyView.vue'
import GameView from './views/GameView.vue'

onMounted(() => {
  connect()
  // browser baru mengizinkan suara setelah ada sentuhan/klik pertama
  // (Safari/iOS lebih mengandalkan click & touchend daripada pointerdown)
  const unlock = () => music.unlock()
  for (const ev of ['pointerdown', 'pointerup', 'click', 'touchend', 'keydown']) {
    document.addEventListener(ev, unlock, { capture: true, passive: true })
  }
})

const showError = computed({
  get: () => !!store.error,
  set: (v) => {
    if (!v) store.error = null
  },
})
const isRuleCode = computed(() => /^R\d+$/.test(store.error?.code || ''))
</script>
