<template>
  <v-main class="felt">
    <v-container style="max-width: 520px" class="py-8">
      <v-card rounded="xl">
        <v-card-text class="text-center pt-6">
          <div class="text-overline">Kode room</div>
          <div class="room-code">{{ room.code }}</div>
          <v-btn variant="tonal" prepend-icon="mdi-share-variant" class="mt-2" @click="share">
            {{ copied ? 'Link disalin' : 'Bagikan link' }}
          </v-btn>
        </v-card-text>

        <v-divider />

        <v-list lines="one">
          <v-list-subheader>
            Pemain ({{ room.members.length }}/{{ room.maxPlayers }})
          </v-list-subheader>
          <v-list-item v-for="m in room.members" :key="m.id">
            <template #prepend>
              <v-avatar :color="m.bot ? 'secondary' : m.connected ? 'primary' : 'grey'" size="34">
                <v-icon v-if="m.bot" icon="mdi-robot" color="white" size="20" />
                <span v-else class="text-white font-weight-bold">{{ m.name.charAt(0).toUpperCase() }}</span>
              </v-avatar>
            </template>
            <v-list-item-title>
              {{ m.name }}
              <span v-if="m.id === store.you?.id" class="text-medium-emphasis">(kamu)</span>
            </v-list-item-title>
            <template #append>
              <v-chip v-if="m.id === room.hostId" size="small" color="secondary" variant="flat">Pembuat</v-chip>
              <v-chip v-if="m.bot" size="small" variant="tonal">bot</v-chip>
              <v-chip v-else-if="!m.connected" size="small" class="ml-1">offline</v-chip>
              <v-btn
                v-if="m.bot && isHost"
                icon="mdi-close"
                size="small"
                variant="text"
                title="Keluarkan bot"
                @click="actions.removeBot(m.id)"
              />
            </template>
          </v-list-item>
        </v-list>

        <v-card-text>
          <v-btn
            v-if="isHost"
            block
            variant="tonal"
            color="secondary"
            prepend-icon="mdi-robot"
            class="mb-3"
            :disabled="full"
            @click="actions.addBot()"
          >
            {{ full ? 'Kursi sudah penuh' : 'Tambah bot' }}
          </v-btn>
          <v-alert v-if="!enough" type="info" variant="tonal" density="compact">
            Menunggu pemain… butuh minimal {{ room.minPlayers }} pemain.
            <span v-if="isHost">Kursi kosong bisa diisi bot.</span>
          </v-alert>
          <v-alert v-else-if="!isHost" type="success" variant="tonal" density="compact">
            Pemain cukup. Menunggu pembuat room memulai permainan.
          </v-alert>
        </v-card-text>

        <v-divider />
        <div class="px-3 pt-2">
          <div class="text-subtitle-2 px-1">Chat</div>
          <ChatPanel height="300px" />
        </div>

        <v-card-actions class="px-4 pb-4">
          <v-btn variant="text" color="error" prepend-icon="mdi-logout" @click="actions.leave()">Keluar</v-btn>
          <v-spacer />
          <v-btn
            v-if="isHost"
            color="primary"
            variant="flat"
            size="large"
            prepend-icon="mdi-cards-playing"
            :disabled="!enough"
            @click="actions.start()"
          >
            Mulai
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-container>
  </v-main>
</template>

<script setup>
import { computed, ref } from 'vue'
import { store, actions } from '../store'
import ChatPanel from '../components/ChatPanel.vue'

const room = computed(() => store.room)
const isHost = computed(() => store.you?.id === room.value.hostId)
const enough = computed(() => room.value.members.length >= room.value.minPlayers)
const full = computed(() => room.value.members.length >= room.value.maxPlayers)
const copied = ref(false)

async function share() {
  const url = `${location.origin}${location.pathname}?room=${room.value.code}`
  if (navigator.share) {
    try {
      await navigator.share({ title: 'Main Perahu-Perahu', text: `Gabung room ${room.value.code}`, url })
      return
    } catch {
      /* dibatalkan, lanjut salin */
    }
  }
  try {
    await navigator.clipboard.writeText(url)
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  } catch {
    prompt('Salin link ini:', url)
  }
}
</script>

<style scoped>
.room-code {
  font-size: 48px;
  font-weight: 800;
  letter-spacing: 0.25em;
  color: #166534;
  line-height: 1.1;
}
</style>
