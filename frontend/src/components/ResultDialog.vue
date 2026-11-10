<template>
  <v-dialog v-model="open" max-width="560" persistent scrollable>
    <v-card>
      <v-card-title class="text-center pt-5">
        <div class="text-h5 font-weight-bold">🏆 {{ results[0]?.name }} menang!</div>
        <v-chip class="mt-2" size="small" :color="murni ? 'success' : 'grey'" variant="tonal">
          {{ murni ? 'Selesai murni: kartu habis' : 'Semua pemain mati' }}
        </v-chip>
      </v-card-title>
      <v-card-text>
        <div v-for="r in results" :key="r.playerId" class="row" :class="{ me: r.playerId === store.you?.id }">
          <div class="place">{{ medal(r.place) }}</div>
          <div class="flex-grow-1">
            <div class="font-weight-bold">
              {{ r.name }}
              <v-chip size="x-small" class="ml-1" :color="chipColor(r)" variant="tonal">{{ statusText(r) }}</v-chip>
            </div>
            <div class="cards">
              <PlayingCard v-for="c in r.cards" :key="c.id" :card="c" small class="mini" />
              <span v-if="!r.cards.length" class="text-caption text-medium-emphasis">Kartu habis</span>
            </div>
          </div>
          <div class="value">
            <div class="text-h6">{{ r.value }}</div>
            <div class="text-caption text-medium-emphasis">nilai</div>
          </div>
        </div>
      </v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-btn variant="text" @click="open = false">Lihat meja</v-btn>
        <v-btn variant="text" color="error" @click="actions.leave()">Keluar</v-btn>
        <v-spacer />
        <v-btn v-if="isHost" color="primary" variant="flat" @click="actions.restart()">Main lagi</v-btn>
        <span v-else class="text-caption">Menunggu pembuat room…</span>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { store, actions } from '../store'
import PlayingCard from './PlayingCard.vue'

const open = defineModel({ type: Boolean, default: true })
const results = computed(() => store.game?.results || [])
const isHost = computed(() => store.you?.id === store.room?.hostId)
const murni = computed(() => results.value[0]?.status === 'FINISHED')

function medal(place) {
  return { 1: '🥇', 2: '🥈', 3: '🥉' }[place] || `#${place}`
}
function statusText(r) {
  if (r.status === 'FINISHED') return 'kartu habis'
  if (r.status === 'DEAD') return `mati ke-${r.deadOrder}`
  return 'masih jalan'
}
function chipColor(r) {
  return r.status === 'FINISHED' ? 'success' : r.status === 'DEAD' ? 'grey' : 'primary'
}
</script>

<style scoped>
.row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 6px;
  border-bottom: 1px solid #e5e7eb;
}
.row.me {
  background: #f0fdf4;
}
.place {
  font-size: 22px;
  width: 36px;
  text-align: center;
}
.cards {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  margin-top: 4px;
}
.mini {
  transform: scale(0.8);
  margin: -5px -4px;
}
.value {
  text-align: right;
  min-width: 48px;
}
</style>
