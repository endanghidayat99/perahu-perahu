<template>
  <v-main class="felt">
    <v-container class="fill-height d-flex justify-center" style="max-width: 460px">
      <div class="w-100">
        <div class="text-center text-white mb-6">
          <div class="text-h3 font-weight-bold">⛵ Perahu-Perahu</div>
          <div class="text-subtitle-1 mt-1" style="opacity: 0.85">Remi khas Ampana · 4–5 pemain</div>
        </div>

        <v-card rounded="xl" class="pa-2">
          <v-card-text>
            <v-text-field
              v-model="name"
              label="Nama pemain"
              prepend-inner-icon="mdi-account"
              maxlength="16"
              counter
              autofocus
              @keyup.enter="code ? join() : create()"
            />

            <v-tabs v-model="tab" grow color="primary" class="mb-4">
              <v-tab value="join">Gabung room</v-tab>
              <v-tab value="create">Buat room</v-tab>
            </v-tabs>

            <v-window v-model="tab">
              <v-window-item value="join">
                <v-text-field
                  v-model="code"
                  label="Kode room"
                  prepend-inner-icon="mdi-door"
                  maxlength="4"
                  class="code-input"
                  @update:model-value="(v) => (code = v.toUpperCase())"
                  @keyup.enter="join"
                />
                <v-btn block size="large" color="primary" :disabled="!canJoin" @click="join">
                  Gabung
                </v-btn>
              </v-window-item>
              <v-window-item value="create">
                <p class="text-body-2 text-medium-emphasis mb-4">
                  Kamu jadi pembuat room. Bagikan kode atau link ke teman, atau isi kursi dengan bot, lalu mulai setelah 4–5 pemain siap.
                </p>
                <v-btn block size="large" color="primary" :disabled="!name.trim()" @click="create">
                  Buat room baru
                </v-btn>
              </v-window-item>
            </v-window>
          </v-card-text>
        </v-card>

        <div class="text-center mt-4 text-caption text-white" style="opacity: 0.7">
          Tidak perlu daftar atau login.
        </div>
      </div>
    </v-container>
  </v-main>
</template>

<script setup>
import { computed, ref } from 'vue'
import { store, actions } from '../store'

const urlCode = new URLSearchParams(location.search).get('room') || ''
const name = ref(store.lastName)
const code = ref(urlCode.toUpperCase())
const tab = ref(urlCode ? 'join' : 'create')

const canJoin = computed(() => name.value.trim() && code.value.trim().length === 4)

function join() {
  if (canJoin.value) actions.join(code.value.trim(), name.value.trim())
}
function create() {
  if (name.value.trim()) actions.create(name.value.trim())
}
</script>

<style scoped>
.code-input :deep(input) {
  letter-spacing: 0.4em;
  font-weight: 700;
  text-transform: uppercase;
}
</style>
