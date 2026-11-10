<template>
  <v-app-bar color="green-darken-4" density="compact" flat>
    <v-app-bar-title>
      <span class="font-weight-bold">⛵ {{ store.room.code }}</span>
      <span class="text-caption ml-2" style="opacity: 0.8">sisa tumpukan {{ game.stockSize }}</span>
    </v-app-bar-title>
    <v-btn
      v-if="store.room.phase === 'FINISHED'"
      icon="mdi-trophy"
      color="amber"
      title="Hasil"
      @click="showResult = true"
    />
    <v-menu :close-on-content-click="false" location="bottom end">
      <template #activator="{ props }">
        <v-btn
          v-bind="props"
          :icon="musicState.muted || musicState.volume === 0 ? 'mdi-music-off' : 'mdi-music'"
          :class="{ pulse: musicState.blocked }"
          title="Musik"
        />
      </template>
      <v-card min-width="260" class="pa-2">
        <div class="d-flex align-center px-2 pt-1">
          <span class="text-subtitle-2">Musik latar</span>
          <v-spacer />
          <v-btn
            size="small"
            variant="tonal"
            :prepend-icon="musicState.muted ? 'mdi-volume-off' : 'mdi-volume-high'"
            @click="music.toggleMute()"
          >
            {{ musicState.muted ? 'Nyalakan' : 'Bisukan' }}
          </v-btn>
        </div>
        <v-slider
          :model-value="musicState.muted ? 0 : musicState.volume"
          min="0"
          max="100"
          step="5"
          hide-details
          color="primary"
          prepend-icon="mdi-volume-low"
          append-icon="mdi-volume-high"
          class="px-2 pt-2"
          @update:model-value="(v) => music.setVolume(v)"
        />
        <v-divider class="my-2" />
        <div class="d-flex align-center px-2">
          <span class="text-caption text-medium-emphasis">Lagu</span>
          <v-spacer />
          <v-btn size="small" variant="tonal" prepend-icon="mdi-skip-next" @click="music.nextTrack()">
            Ganti musik
          </v-btn>
        </div>
        <v-list density="compact" class="py-0">
          <v-list-item
            v-for="(t, i) in TRACKS"
            :key="t.name"
            :title="t.name"
            :active="i === musicState.track"
            color="primary"
            rounded="lg"
            @click="music.setTrack(i)"
          >
            <template #prepend>
              <v-icon :icon="i === musicState.track ? 'mdi-play-circle' : 'mdi-music-note-outline'" size="18" />
            </template>
          </v-list-item>
        </v-list>
        <v-divider class="my-2" />
        <div class="d-flex align-center px-2 pb-1">
          <v-icon icon="mdi-bell-ring-outline" size="18" class="mr-2" />
          <span class="text-body-2">Bel giliran & notifikasi</span>
          <v-spacer />
          <v-switch
            :model-value="musicState.sfx"
            color="primary"
            density="compact"
            hide-details
            inset
            @update:model-value="sfx.toggle()"
          />
        </div>
        <div v-if="musicState.blocked" class="text-caption text-medium-emphasis px-2 pb-1">
          Ketuk di mana saja untuk memulai musik.
        </div>
      </v-card>
    </v-menu>
    <v-btn title="Chat" @click="openChat" icon>
      <v-badge v-if="unread" :content="unread" color="error" floating>
        <v-icon icon="mdi-chat-outline" />
      </v-badge>
      <v-icon v-else icon="mdi-chat-outline" />
    </v-btn>
    <v-btn icon="mdi-history" title="Riwayat" @click="showLog = true" />
    <v-btn icon="mdi-help-circle-outline" title="Aturan" @click="showRules = true" />
    <v-btn icon="mdi-logout" title="Keluar" @click="confirmLeave = true" />
  </v-app-bar>

  <v-main class="felt">
    <div class="game-wrap">
      <!-- pemain -->
      <div class="players" :class="{ talking: Object.keys(bubbles).length > 0 }">
        <div
          v-for="p in game.players"
          :key="p.id"
          class="player"
          :class="{ turn: p.id === game.turnPlayerId, dead: p.status === 'DEAD', me: p.id === myId }"
        >
          <span v-if="p.status === 'DEAD'" class="dead-badge">MATI</span>
          <transition name="bubble">
            <div v-if="bubbles[p.id]" :key="bubbles[p.id].id" class="speech" @click="openChat">
              <div class="speech-text">{{ bubbles[p.id].text }}</div>
            </div>
          </transition>
          <div class="pname">
            <v-icon v-if="p.status === 'DEAD'" size="14" icon="mdi-skull-outline" />
            <v-icon v-else-if="p.status === 'FINISHED'" size="14" icon="mdi-trophy" color="amber" />
            <v-icon v-else-if="p.bot" size="14" icon="mdi-robot" />
            <v-icon v-else-if="!p.connected" size="14" icon="mdi-wifi-off" />
            {{ p.name }}<span v-if="p.id === myId"> (kamu)</span>
          </div>
          <div class="pinfo">
            <v-icon size="13" icon="mdi-cards-outline" /> {{ p.handCount }}
            <span v-if="p.hasDasar" class="ml-1 dasar">dasar ✓</span>
          </div>
        </div>
      </div>

      <!-- status -->
      <div class="status" :class="{ mine: myTurn }">{{ statusText }}</div>
      <v-alert
        v-if="musicState.blocked"
        density="compact"
        type="info"
        variant="tonal"
        icon="mdi-volume-high"
        class="mb-2 sound-hint"
        @click="music.unlock()"
      >
        Ketuk di sini untuk menyalakan suara.
      </v-alert>

      <!-- meja -->
      <div class="table">
        <div v-if="!game.piles.length" class="empty">Meja masih kosong. Pemain pertama menurunkan dasar.</div>
        <div
          v-for="pile in game.piles"
          :key="pile.id"
          class="pile"
          :class="{ target: canTarget(pile), closed: pile.closed }"
          @click="playTo(pile)"
        >
          <div class="pile-head">
            <span>{{ pileTitle(pile) }}</span>
            <span v-if="pile.type === 'RUN'" class="range">{{ pileRange(pile) }}</span>
            <span class="owner">{{ pile.ownerName }}</span>
            <v-icon v-if="pile.closed" size="14" icon="mdi-lock" title="Ditutup joker" />
          </div>
          <!-- jarak tumpuk menyusut otomatis supaya urutan panjang tidak keluar dari kotaknya -->
          <div class="pile-cards">
            <div
              v-for="(c, i) in pile.cards"
              :key="c.id"
              class="slot"
              :class="{ last: i === pile.cards.length - 1 }"
            >
              <PlayingCard :card="c" small />
            </div>
          </div>
        </div>
      </div>
    </div>
  </v-main>

  <!-- tangan -->
  <v-footer app class="hand-bar" elevation="8">
    <div class="w-100">
      <div class="d-flex align-center flex-wrap ga-2 mb-1">
        <v-btn
          size="small"
          color="primary"
          variant="flat"
          :disabled="handDisabled || !myTurn || selected.length < 3"
          prepend-icon="mdi-arrow-up-bold-box"
          @click="playNew"
        >
          {{ me?.hasDasar ? 'Tumpukan baru' : 'Turunkan dasar' }}
        </v-btn>
        <v-btn size="small" variant="text" :disabled="handDisabled || !selected.length" @click="selected = []">
          Batal pilih
        </v-btn>
        <v-spacer />
        <v-btn size="small" variant="text" @click="sortByRank = !sortByRank">
          Urut: {{ sortByRank ? 'angka' : 'jenis' }}
        </v-btn>
        <span class="text-caption text-medium-emphasis">{{ hand.length }} kartu · nilai {{ handValue }}</span>
      </div>
      <!-- semua kartu selalu terlihat: jarak tumpuk menyesuaikan lebar layar, di HP dibagi 2 baris -->
      <div ref="handEl" class="hand" :class="{ disabled: handDisabled }">
        <div v-for="(row, r) in handRows" :key="r" class="hand-row">
          <div
            v-for="(c, i) in row"
            :key="c.id"
            class="hand-slot"
            :style="{ marginLeft: i === 0 ? '0px' : handLayout.offset + 'px' }"
          >
            <PlayingCard
              :card="c"
              :selected="selected.includes(c.id)"
              :compact="handLayout.compact"
              :clickable="!handDisabled"
              @click="toggle(c)"
            />
          </div>
        </div>
      </div>
      <div v-if="me?.status === 'DEAD'" class="text-caption text-center mt-1 text-error font-weight-medium">
        Kamu sudah mati. Sisa kartu tidak bisa dimainkan lagi dan dihitung di akhir (nilai {{ handValue }}).
      </div>
      <div v-else-if="myTurn && selected.length" class="text-caption text-center mt-1">
        {{ selected.length }} kartu dipilih — ketuk tumpukan di meja untuk menyambung
      </div>
    </div>
  </v-footer>

  <!-- chat -->
  <v-navigation-drawer v-model="showChat" location="right" temporary width="320" class="chat-drawer">
    <div class="d-flex flex-column h-100">
      <div class="d-flex align-center px-3 pt-2">
        <span class="text-subtitle-1 font-weight-bold">Chat room</span>
        <v-spacer />
        <v-btn icon="mdi-close" variant="text" size="small" @click="showChat = false" />
      </div>
      <ChatPanel v-if="showChat" class="flex-grow-1 px-2 pb-2" />
    </div>
  </v-navigation-drawer>

  <!-- riwayat -->
  <v-navigation-drawer v-model="showLog" location="right" temporary width="300">
    <v-list density="compact">
      <v-list-subheader>Riwayat permainan</v-list-subheader>
      <v-list-item v-for="(line, i) in reversedLog" :key="i" :title="line" class="log-line" />
    </v-list>
  </v-navigation-drawer>

  <!-- pilihan joker -->
  <v-dialog :model-value="!!store.ambiguity" max-width="420" persistent>
    <v-card title="Taruh joker di mana?">
      <v-list>
        <v-list-item
          v-for="(opt, i) in store.ambiguity?.options || []"
          :key="i"
          :title="opt"
          prepend-icon="mdi-cards-playing-outline"
          @click="actions.chooseAmbiguity(i)"
        />
      </v-list>
      <v-card-actions>
        <v-spacer />
        <v-btn @click="store.ambiguity = null">Batal</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>

  <RulesDialog v-model="showRules" />
  <ResultDialog v-if="store.room.phase === 'FINISHED'" v-model="showResult" />

  <v-dialog v-model="confirmLeave" max-width="360">
    <v-card title="Keluar dari permainan?">
      <v-card-text>Kursimu tetap ada. Kalau giliranmu tiba, server menjalankan langkah otomatis.</v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn @click="confirmLeave = false">Batal</v-btn>
        <v-btn color="error" @click="actions.leave()">Keluar</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { store, actions, unreadChat } from '../store'
import { music, musicState, sfx, TRACKS } from '../music'
import PlayingCard from '../components/PlayingCard.vue'
import RulesDialog from '../components/RulesDialog.vue'
import ChatPanel from '../components/ChatPanel.vue'
import ResultDialog from '../components/ResultDialog.vue'

const SYMBOLS = { S: '♠', H: '♥', D: '♦', C: '♣' }
const RANKS = { 1: 'A', 11: 'J', 12: 'Q', 13: 'K' }

const game = computed(() => store.game)
const myId = computed(() => store.you?.id)
const me = computed(() => game.value.players.find((p) => p.id === myId.value))
const myTurn = computed(() => game.value.turnPlayerId === myId.value && store.room.phase === 'PLAYING')
const turnPlayer = computed(() => game.value.players.find((p) => p.id === game.value.turnPlayerId))

const selected = ref([])
const sortByRank = ref(false)
const showLog = ref(false)
const showRules = ref(false)
const confirmLeave = ref(false)
const showResult = ref(true)

// ---------------------------------------------------------------- chat
const showChat = ref(false)
const unread = computed(() => unreadChat())
function openChat() {
  showChat.value = true
}

// gelembung chat di atas nama pemain yang mengirim pesan
const bubbles = reactive({}) // playerId -> { id, text }
const bubbleTimers = {}
let lastBubbleId = Math.max(0, ...(store.room.chat || []).map((c) => c.id)) // pesan lama tidak dimunculkan
watch(
  () => store.room.chat?.length,
  () => {
    for (const c of store.room.chat || []) {
      if (c.id <= lastBubbleId) continue
      lastBubbleId = c.id
      if (!c.playerId) continue
      bubbles[c.playerId] = { id: c.id, text: c.text }
      clearTimeout(bubbleTimers[c.playerId])
      const ms = Math.min(8000, 3500 + c.text.length * 50) // pesan panjang tampil lebih lama
      bubbleTimers[c.playerId] = setTimeout(() => delete bubbles[c.playerId], ms)
    }
  },
)
onUnmounted(() => Object.values(bubbleTimers).forEach(clearTimeout))

// musik santai selama di meja permainan
onMounted(() => music.start())
onUnmounted(() => {
  music.stop()
  document.title = BASE_TITLE
})

// ---------------------------------------------------------------- notifikasi giliran & mati
const BASE_TITLE = 'Perahu-Perahu'

// Bunyikan sekali per kejadian. Kuncinya memakai nomor permainan dari server, jadi kejadian
// yang sudah terjadi saat meja baru dibuka (mis. langsung mati di giliran pertama) tetap berbunyi,
// tetapi tidak diputar ulang saat halaman di-refresh.
function once(key) {
  const k = `perahu.sfx.${store.room.code}.${game.value.gameNo}.${key}`
  if (sessionStorage.getItem(k)) return false
  sessionStorage.setItem(k, '1')
  return true
}

watch(
  () => (myTurn.value ? game.value.version : null),
  (turnVersion) => {
    if (turnVersion == null) {
      document.title = BASE_TITLE
      return
    }
    if (!once(`turn.${turnVersion}`)) return
    sfx.turn()
    if (document.hidden) document.title = '🔔 Giliranmu! – ' + BASE_TITLE
  },
  { immediate: true },
)
watch(
  () => me.value?.status,
  (now) => {
    if (now !== 'DEAD' || !once('dead')) return
    sfx.dead()
    if (document.hidden) document.title = '💀 Kamu mati – ' + BASE_TITLE
  },
  { immediate: true },
)
function onVisible() {
  if (!document.hidden && !myTurn.value) document.title = BASE_TITLE
}
onMounted(() => document.addEventListener('visibilitychange', onVisible))
onUnmounted(() => document.removeEventListener('visibilitychange', onVisible))

const hand = computed(() => {
  const h = [...(game.value.hand || [])]
  if (sortByRank.value) {
    const r = (c) => (c.suit === 'JOKER' ? 99 : c.rank === 1 ? 14 : c.rank)
    h.sort((a, b) => r(a) - r(b) || a.suit.localeCompare(b.suit))
  }
  return h
})

const handValue = computed(() =>
  (game.value.hand || []).reduce((sum, c) => {
    if (c.suit === 'JOKER') return sum + 25
    if (c.rank === 1) return sum + 15
    if (c.rank >= 11) return sum + 10
    return sum + c.rank
  }, 0),
)

const reversedLog = computed(() => [...(game.value.log || [])].reverse())

// ---------------------------------------------------------------- tata letak kartu tangan
const handEl = ref(null)
const handWidth = ref(360)
let resizeObs = null
onMounted(() => {
  resizeObs = new ResizeObserver(([entry]) => {
    handWidth.value = entry.contentRect.width
  })
  if (handEl.value) resizeObs.observe(handEl.value)
})
onUnmounted(() => resizeObs?.disconnect())

const handLayout = reactive({ compact: false, rows: 1, perRow: 1, offset: 0 })
watch(
  () => [hand.value.length, handWidth.value],
  ([n, width]) => {
    const compact = width < 600
    const cardW = compact ? 46 : 56
    const minStep = compact ? 24 : 28 // bagian kartu yang terlihat paling sedikit (masih mudah disentuh)
    const maxStep = cardW + 4 // tanpa tumpuk
    let rows = 1
    while (rows < 3 && (Math.ceil(n / rows) - 1) * minStep + cardW > width) rows++
    const perRow = Math.max(1, Math.ceil(n / rows))
    const step = perRow > 1 ? Math.min(maxStep, (width - cardW) / (perRow - 1)) : maxStep
    Object.assign(handLayout, { compact, rows, perRow, offset: Math.floor(step - cardW) })
  },
  { immediate: true },
)
const handRows = computed(() => {
  const out = []
  for (let i = 0; i < hand.value.length; i += handLayout.perRow) out.push(hand.value.slice(i, i + handLayout.perRow))
  return out
})

// kartu pemain yang sudah mati (atau permainan selesai) tidak bisa dipilih lagi
const handDisabled = computed(() => me.value?.status === 'DEAD' || store.room.phase === 'FINISHED')
watch(handDisabled, (d) => {
  if (d) selected.value = []
})

// buang pilihan yang sudah tidak ada di tangan (setelah langkah berhasil)
watch(
  () => game.value.hand,
  (h) => {
    const ids = new Set((h || []).map((c) => c.id))
    selected.value = selected.value.filter((id) => ids.has(id))
  },
)

const statusText = computed(() => {
  if (store.room.phase === 'FINISHED') {
    const w = game.value.results?.[0]
    return w?.status === 'FINISHED'
      ? `Selesai murni: ${w.name} menghabiskan semua kartu. Sisa kartu dihitung.`
      : 'Permainan selesai: semua pemain mati. Sisa kartu dihitung.'
  }
  if (me.value?.status === 'DEAD') return 'Kamu sudah mati. Sisa kartumu dihitung di akhir.'
  if (myTurn.value && !me.value.hasDasar)
    return 'Giliranmu: pilih minimal 3 kartu berurutan satu jenis tanpa joker (atau 5 kartu sama angka), lalu "Turunkan dasar".'
  if (myTurn.value) return 'Giliranmu: pilih kartu lalu ketuk tumpukan untuk menyambung.'
  return `Menunggu giliran ${turnPlayer.value?.name || ''}…`
})

function toggle(card) {
  if (handDisabled.value) return
  const i = selected.value.indexOf(card.id)
  if (i >= 0) selected.value.splice(i, 1)
  else if (selected.value.length < 5) selected.value.push(card.id)
  else store.error = { code: 'R6', message: 'Maksimal 5 kartu sekali turun.' }
}

function canTarget(pile) {
  return myTurn.value && selected.value.length > 0 && !pile.closed && me.value?.hasDasar
}

function playTo(pile) {
  if (!myTurn.value || !selected.value.length) return
  actions.play(pile.id, [...selected.value])
}

function playNew() {
  actions.play(null, [...selected.value])
}

function cardText(c) {
  if (c.suit === 'JOKER') return (c.as || '').replace(/[♠♥♦♣]$/, '')
  return RANKS[c.rank] || String(c.rank)
}

function pileRange(pile) {
  const cards = pile.cards
  if (!cards.length) return ''
  return `${cardText(cards[0])}–${cardText(cards[cards.length - 1])}`
}

function pileTitle(pile) {
  if (pile.type === 'RUN') return `Urutan ${SYMBOLS[pile.suit]}`
  return `Sama angka ${RANKS[pile.rank] || pile.rank}`
}
</script>

<style scoped>
.game-wrap {
  padding: 8px 8px 230px;
  max-width: 1100px;
  margin: 0 auto;
}
.players {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  padding-bottom: 4px;
  transition: padding-top 0.2s ease;
}
.player {
  flex: 1 0 auto;
  min-width: 92px;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  border-radius: 10px;
  padding: 6px 8px;
  border: 2px solid transparent;
}
.player.turn {
  border-color: #fbbf24;
  background: rgba(251, 191, 36, 0.22);
}
.player {
  position: relative;
}
.player.dead {
  background: rgba(0, 0, 0, 0.25);
}
.player.dead .pname,
.player.dead .pinfo {
  opacity: 0.55;
}
.dead-badge {
  position: absolute;
  top: 4px;
  right: 6px;
  background: #b91c1c;
  color: #fff;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.06em;
  line-height: 1;
  padding: 3px 5px;
  border-radius: 4px;
}
.player.dead .pname {
  padding-right: 38px;
}
.player.me .pname {
  text-decoration: underline;
}
.pname {
  font-weight: 700;
  font-size: 13px;
  white-space: nowrap;
}
.pinfo {
  font-size: 12px;
  opacity: 0.9;
}
.dasar {
  color: #bbf7d0;
}
.status {
  margin: 8px 0;
  padding: 8px 12px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.25);
  color: #fff;
  font-size: 14px;
}
.status.mine {
  background: #fbbf24;
  color: #111827;
  font-weight: 600;
}
.table {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 8px;
}
.empty {
  color: #fff;
  opacity: 0.75;
  padding: 24px;
  text-align: center;
  grid-column: 1 / -1;
}
.pile {
  background: rgba(255, 255, 255, 0.1);
  border: 2px solid rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  padding: 6px 8px 8px;
}
.pile.target {
  border-color: #fbbf24;
  cursor: pointer;
  background: rgba(251, 191, 36, 0.15);
}
.pile.closed {
  opacity: 0.75;
}
.pile-head {
  display: flex;
  gap: 6px;
  align-items: center;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 4px;
}
.owner {
  opacity: 0.7;
  font-weight: 400;
  margin-left: auto;
}
.pile-cards {
  display: flex;
  min-width: 0;
  overflow: hidden;
  padding-right: 2px;
}
.slot {
  /* bagian kartu yang terlihat: 22px saat lega, menyusut sampai 12px (angka pojok tetap terbaca) */
  flex: 0 1 22px;
  min-width: 12px;
}
.slot.last {
  flex: 0 0 auto;
}
.range {
  font-weight: 400;
  opacity: 0.85;
}
.hand-bar {
  flex-direction: column;
  padding: 8px 8px 10px;
}
.hand {
  padding-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.hand-row {
  display: flex;
}
.hand-slot {
  flex: 0 0 auto;
}
.hand.disabled {
  opacity: 0.45;
  filter: grayscale(0.8);
  pointer-events: none;
}
.players.talking {
  padding-top: 50px; /* ruang untuk gelembung chat (maks. 2 baris) */
}
.speech {
  position: absolute;
  bottom: calc(100% + 7px);
  left: 4px;
  width: max-content;
  max-width: 190px;
  background: #fff;
  color: #111827;
  border-radius: 12px;
  padding: 4px 10px;
  font-size: 13px;
  line-height: 1.3;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.3);
  cursor: pointer;
  z-index: 2;
}
.speech-text {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}
.speech::after {
  content: '';
  position: absolute;
  top: 100%;
  left: 18px;
  border: 6px solid transparent;
  border-top-color: #fff;
}
.bubble-enter-active,
.bubble-leave-active {
  transition: all 0.2s ease;
}
.bubble-enter-from,
.bubble-leave-to {
  opacity: 0;
  transform: translateY(6px) scale(0.95);
}
.sound-hint {
  cursor: pointer;
  background: #fff !important;
}
.pulse {
  animation: pulse 1.2s ease-in-out infinite;
}
@keyframes pulse {
  50% {
    opacity: 0.4;
  }
}
.log-line :deep(.v-list-item-title) {
  white-space: normal;
  font-size: 13px;
}
</style>
