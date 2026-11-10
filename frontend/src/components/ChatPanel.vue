<template>
  <div class="chat" :style="{ height }">
    <div ref="listEl" class="messages">
      <div v-if="!messages.length" class="empty">Belum ada pesan. Sapa pemain lain 👋</div>
      <template v-for="m in messages" :key="m.id">
        <div v-if="!m.playerId" class="system">{{ m.text }}</div>
        <div v-else class="msg" :class="{ mine: m.playerId === store.you?.id }">
          <div class="bubble">
            <div v-if="m.playerId !== store.you?.id" class="who">{{ m.name }}</div>
            <div class="text">{{ m.text }}</div>
            <div class="time">{{ clock(m.time) }}</div>
          </div>
        </div>
      </template>
    </div>

    <div class="quick">
      <v-chip v-for="q in QUICK" :key="q" size="small" variant="tonal" class="mr-1" @click="send(q)">{{ q }}</v-chip>
    </div>

    <form class="composer" @submit.prevent="send(draft)">
      <v-text-field
        v-model="draft"
        placeholder="Tulis pesan…"
        density="compact"
        hide-details
        maxlength="200"
        variant="outlined"
        autocomplete="off"
        class="flex-grow-1"
      />
      <v-btn type="submit" icon="mdi-send" color="primary" variant="flat" size="small" :disabled="!draft.trim()" />
    </form>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { store, actions, markChatSeen } from '../store'

defineProps({ height: { type: String, default: '100%' } })

const QUICK = ['👍', '😂', '😮', '😢', 'GG', 'Ayo cepat!']
const draft = ref('')
const listEl = ref(null)
const messages = computed(() => store.room?.chat || [])

function send(text) {
  const t = (text || '').trim()
  if (!t) return
  actions.chat(t)
  if (text === draft.value) draft.value = ''
}

function clock(ms) {
  return new Date(ms).toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' })
}

async function scrollToEnd() {
  await nextTick()
  if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
}

// panel yang sedang terbuka menandai semua pesan sudah dibaca
watch(
  () => messages.value.length,
  () => {
    markChatSeen()
    scrollToEnd()
  },
)
onMounted(() => {
  markChatSeen()
  scrollToEnd()
})
</script>

<style scoped>
.chat {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.messages {
  flex: 1 1 auto;
  overflow-y: auto;
  padding: 8px 4px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.empty {
  text-align: center;
  color: #6b7280;
  font-size: 13px;
  margin: auto;
}
.system {
  align-self: center;
  font-size: 11.5px;
  color: #6b7280;
  background: #f3f4f6;
  border-radius: 10px;
  padding: 2px 10px;
  text-align: center;
}
.msg {
  display: flex;
}
.msg.mine {
  justify-content: flex-end;
}
.bubble {
  max-width: 80%;
  background: #f1f5f9;
  border-radius: 12px 12px 12px 4px;
  padding: 5px 10px 3px;
}
.mine .bubble {
  background: #dcfce7;
  border-radius: 12px 12px 4px 12px;
}
.who {
  font-size: 11.5px;
  font-weight: 700;
  color: #166534;
}
.text {
  font-size: 14px;
  white-space: pre-wrap;
  word-break: break-word;
}
.time {
  font-size: 10px;
  color: #9ca3af;
  text-align: right;
}
.quick {
  padding: 4px 4px 0;
  white-space: nowrap;
  overflow-x: auto;
}
.composer {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 6px 4px 4px;
}
</style>
