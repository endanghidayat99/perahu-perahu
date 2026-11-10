import { reactive } from 'vue'

const SESSION_KEY = 'perahu.session'
const NAME_KEY = 'perahu.name'

function loadSession() {
  try {
    return JSON.parse(sessionStorage.getItem(SESSION_KEY) || 'null')
  } catch {
    return null
  }
}

export const store = reactive({
  connected: false,
  session: loadSession(), // { code, playerId, token, name }
  room: null,
  game: null,
  you: null,
  error: null, // { code, message }
  ambiguity: null, // { options, payload }
  lastName: localStorage.getItem(NAME_KEY) || '',
  chatSeen: Number(sessionStorage.getItem('perahu.chatSeen') || 0), // id pesan chat terakhir yang sudah dilihat
})

/** Jumlah pesan dari pemain lain yang belum dibaca. */
export function unreadChat() {
  const me = store.you?.id
  return (store.room?.chat || []).filter((c) => c.id > store.chatSeen && c.playerId && c.playerId !== me).length
}

export function markChatSeen() {
  const chat = store.room?.chat || []
  if (chat.length) {
    store.chatSeen = chat[chat.length - 1].id
    sessionStorage.setItem('perahu.chatSeen', String(store.chatSeen))
  }
}

let ws = null
let retry = 0
let pending = [] // pesan yang dikirim saat koneksi belum siap

function wsUrl() {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  return `${proto}://${location.host}/ws`
}

export function connect() {
  ws = new WebSocket(wsUrl())
  ws.onopen = () => {
    store.connected = true
    retry = 0
    if (store.session) {
      raw({ type: 'rejoin', code: store.session.code, token: store.session.token })
    }
    pending.forEach(raw)
    pending = []
  }
  ws.onmessage = (ev) => handle(JSON.parse(ev.data))
  ws.onclose = () => {
    store.connected = false
    const delay = Math.min(1000 * 2 ** retry, 10000)
    retry++
    setTimeout(connect, delay)
  }
}

function raw(msg) {
  ws.send(JSON.stringify(msg))
}

function send(msg) {
  if (ws && ws.readyState === WebSocket.OPEN) raw(msg)
  else pending.push(msg)
}

function handle(msg) {
  switch (msg.type) {
    case 'joined':
      store.session = { code: msg.code, playerId: msg.playerId, token: msg.token, name: msg.name }
      sessionStorage.setItem(SESSION_KEY, JSON.stringify(store.session))
      history.replaceState(null, '', `?room=${msg.code}`)
      break
    case 'state':
      store.room = msg.room
      store.you = msg.you
      store.game = msg.game
      break
    case 'error':
      if (msg.code === 'REJOIN_FAILED') {
        clearSession()
        return
      }
      if (msg.code === 'AMBIGUOUS' && lastPlay) {
        store.ambiguity = { options: msg.options, payload: lastPlay }
        return
      }
      store.error = { code: msg.code, message: msg.message }
      break
  }
}

function clearSession() {
  store.chatSeen = 0
  sessionStorage.removeItem('perahu.chatSeen')
  store.session = null
  store.room = null
  store.game = null
  store.you = null
  sessionStorage.removeItem(SESSION_KEY)
}

let lastPlay = null

export const actions = {
  create(name) {
    rememberName(name)
    send({ type: 'create', name })
  },
  join(code, name) {
    rememberName(name)
    send({ type: 'join', code, name })
  },
  start() {
    send({ type: 'start' })
  },
  addBot() {
    send({ type: 'addBot' })
  },
  removeBot(playerId) {
    send({ type: 'removeBot', playerId })
  },
  /** pileId null = dasar / tumpukan baru */
  play(pileId, cardIds, placement = null) {
    lastPlay = { pileId, cardIds }
    send({ type: 'play', pileId, cardIds, placement })
  },
  chooseAmbiguity(index) {
    const p = store.ambiguity?.payload
    store.ambiguity = null
    if (p) actions.play(p.pileId, p.cardIds, index)
  },
  restart() {
    send({ type: 'restart' })
  },
  chat(text) {
    send({ type: 'chat', text })
  },
  leave() {
    send({ type: 'leave' })
    clearSession()
    history.replaceState(null, '', location.pathname)
  },
}

function rememberName(name) {
  store.lastName = name
  localStorage.setItem(NAME_KEY, name)
}
