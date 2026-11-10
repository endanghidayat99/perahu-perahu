// Musik latar santai yang dibangkitkan langsung di browser (Web Audio API):
// pad akor lembut + petikan melodi pentatonik + bass pelan, dengan gema.
// Tanpa file audio, jadi bebas hak cipta dan tidak menambah ukuran aplikasi.
import { reactive } from 'vue'

const PREF_KEY = 'perahu.music'

function loadPrefs() {
  try {
    return { volume: 60, muted: false, track: 0, sfx: true, ...JSON.parse(localStorage.getItem(PREF_KEY) || '{}') }
  } catch {
    return { volume: 60, muted: false, track: 0, sfx: true }
  }
}

export const musicState = reactive({
  ...loadPrefs(),
  playing: false, // musik diminta berjalan (sedang di meja permainan)
  blocked: false, // browser menahan suara sampai pengguna menyentuh layar
})

// Daftar lagu. Semua dibangkitkan dengan pola yang sama, beda akor, tangga nada, tempo dan warna suara.
// Nada dalam angka MIDI (60 = C tengah).
export const TRACKS = [
  {
    name: 'Senja di Teluk',
    bpm: 68,
    chords: [[57, 60, 64], [53, 57, 60], [55, 60, 64], [55, 59, 62]], // Am F C G
    bass: [45, 41, 48, 43],
    scale: [69, 72, 74, 76, 79, 81, 84, 86, 88], // A minor pentatonik
    pluck: 'triangle',
    pluckLevel: 0.05,
    density: [0.55, 0.25],
    brightness: 3200,
  },
  {
    name: 'Pantai Togean',
    bpm: 84,
    chords: [[60, 64, 67], [57, 60, 64], [65, 69, 72], [67, 71, 74]], // C Am F G
    bass: [48, 45, 41, 43],
    scale: [72, 74, 76, 79, 81, 84, 86, 88], // C mayor pentatonik
    pluck: 'sine',
    pluckLevel: 0.042,
    density: [0.7, 0.45],
    brightness: 4200,
  },
  {
    name: 'Malam di Ampana',
    bpm: 56,
    chords: [[50, 57, 60, 64], [46, 53, 57, 62], [48, 55, 59, 64], [45, 52, 55, 60]], // Dm9 Bbmaj7 C Am
    bass: [38, 34, 36, 33],
    scale: [62, 65, 67, 69, 72, 74, 77, 79], // D minor pentatonik
    pluck: 'sine',
    pluckLevel: 0.045,
    density: [0.35, 0.12],
    brightness: 2200,
  },
  {
    name: 'Kecapi Tojo',
    bpm: 76,
    chords: [[57, 64, 69], [55, 62, 67], [53, 60, 65], [55, 62, 67]], // A5 G5 F5 G5 (kwint, nuansa tradisional)
    bass: [45, 43, 41, 43],
    scale: [69, 70, 74, 76, 77, 81, 82, 86], // pelog-ish: A Bb D E F
    pluck: 'square',
    pluckLevel: 0.022,
    density: [0.65, 0.35],
    brightness: 2800,
  },
]

const track = () => TRACKS[musicState.track % TRACKS.length]
const beat = () => 60 / track().bpm
const barLen = () => beat() * 4

const midiHz = (m) => 440 * 2 ** ((m - 69) / 12)

let ctx = null
let master = null
let tone = null // lowpass: warna suara per lagu
let sfxOut = null // jalur suara notifikasi, terpisah dari volume musik
const active = new Set() // osilator yang sudah dijadwalkan, untuk dihentikan saat ganti lagu
let dry = null
let wet = null
let timer = null
let nextBarTime = 0
let barIndex = 0
let melodyPos = 2

function ensureContext() {
  if (ctx) return ctx
  const AC = window.AudioContext || window.webkitAudioContext
  if (!AC) return null
  ctx = new AC()
  master = ctx.createGain()
  master.gain.value = 0
  tone = ctx.createBiquadFilter()
  tone.type = 'lowpass'
  tone.frequency.value = track().brightness
  master.connect(tone).connect(ctx.destination)

  dry = ctx.createGain()
  dry.gain.value = 0.8
  dry.connect(master)
  wet = ctx.createGain()
  wet.gain.value = 0.45
  const reverb = ctx.createConvolver()
  reverb.buffer = impulse(ctx, 2.8)
  wet.connect(reverb).connect(master)

  sfxOut = ctx.createGain()
  sfxOut.gain.value = 0.9
  sfxOut.connect(ctx.destination)

  ctx.onstatechange = () => {
    musicState.blocked = musicState.playing && ctx.state !== 'running'
  }
  return ctx
}

function impulse(c, seconds) {
  const len = Math.floor(c.sampleRate * seconds)
  const buf = c.createBuffer(2, len, c.sampleRate)
  for (let ch = 0; ch < 2; ch++) {
    const data = buf.getChannelData(ch)
    for (let i = 0; i < len; i++) data[i] = (Math.random() * 2 - 1) * (1 - i / len) ** 2.5
  }
  return buf
}

function voice(type, freq, start, attack, hold, release, level, reverbSend = 0.5) {
  const osc = ctx.createOscillator()
  const env = ctx.createGain()
  osc.type = type
  osc.frequency.value = freq
  env.gain.setValueAtTime(0, start)
  env.gain.linearRampToValueAtTime(level, start + attack)
  env.gain.setValueAtTime(level, start + attack + hold)
  env.gain.exponentialRampToValueAtTime(0.0001, start + attack + hold + release)
  osc.connect(env)
  env.connect(dry)
  const send = ctx.createGain()
  send.gain.value = reverbSend
  env.connect(send).connect(wet)
  osc.start(start)
  osc.stop(start + attack + hold + release + 0.05)
  active.add(osc)
  osc.onended = () => active.delete(osc)
}

function scheduleBar(t) {
  const tr = track()
  const BEAT = beat()
  const BAR = barLen()
  const i = barIndex % tr.chords.length
  // pad: tiap nada dua osilator sedikit berbeda nada supaya terasa hangat
  const padLevel = 0.105 / tr.chords[i].length
  for (const n of tr.chords[i]) {
    voice('sine', midiHz(n), t, 1.4, BAR - 1.6, 1.8, padLevel, 0.6)
    voice('triangle', midiHz(n) * 1.003, t, 1.6, BAR - 1.8, 1.8, padLevel / 2, 0.6)
  }
  // bass di ketukan 1 dan 3
  voice('sine', midiHz(tr.bass[i]), t, 0.05, 0.4, 1.6, 0.07, 0.2)
  voice('sine', midiHz(tr.bass[i]), t + BEAT * 2, 0.05, 0.3, 1.4, 0.05, 0.2)
  // melodi petik: langkah acak pendek di tangga nada lagu, jarang-jarang
  const scale = tr.scale
  for (let step = 0; step < 8; step++) {
    if (Math.random() > (step % 2 === 0 ? tr.density[0] : tr.density[1])) continue
    melodyPos = Math.max(0, Math.min(scale.length - 1, melodyPos + [-2, -1, -1, 0, 1, 1, 2][Math.floor(Math.random() * 7)]))
    const st = t + step * (BEAT / 2) + Math.random() * 0.02
    voice(tr.pluck, midiHz(scale[melodyPos]), st, 0.008, 0.05, 1.3, tr.pluckLevel, 0.55)
  }
  barIndex++
}

function tick() {
  while (nextBarTime < ctx.currentTime + barLen()) {
    scheduleBar(nextBarTime)
    nextBarTime += barLen()
  }
}

function targetGain() {
  return musicState.muted ? 0 : (musicState.volume / 100) * 2.6
}

function applyGain(fade = 0.4) {
  if (!ctx) return
  const now = ctx.currentTime
  master.gain.cancelScheduledValues(now)
  master.gain.setValueAtTime(master.gain.value, now)
  master.gain.linearRampToValueAtTime(musicState.playing ? targetGain() : 0, now + fade)
}

function savePrefs() {
  localStorage.setItem(
    PREF_KEY,
    JSON.stringify({ volume: musicState.volume, muted: musicState.muted, track: musicState.track, sfx: musicState.sfx }),
  )
}

// ctx.resume() harus dipanggil langsung (sinkron) di dalam sentuhan/klik pengguna,
// karena Safari dan sebagian browser HP menolak suara yang dimulai di luar interaksi.
function resume() {
  if (!ctx) return Promise.resolve()
  let p = Promise.resolve()
  if (ctx.state !== 'running') {
    try {
      p = ctx.resume() || Promise.resolve()
    } catch {
      /* diabaikan */
    }
    // trik iOS: putar 1 sampel hening supaya jalur audio benar-benar terbuka
    try {
      const src = ctx.createBufferSource()
      src.buffer = ctx.createBuffer(1, 1, 22050)
      src.connect(ctx.destination)
      src.start(0)
    } catch {
      /* diabaikan */
    }
  }
  return p
    .catch(() => {})
    .then(() => {
      musicState.blocked = musicState.playing && ctx.state !== 'running'
    })
}

// ---------------------------------------------------------------- suara notifikasi

function bellNote(freq, start, level, decay = 1.6) {
  // bel: nada dasar + parsial atas yang cepat hilang
  for (const [mult, amp, d] of [
    [1, 1, decay],
    [2.0, 0.45, decay * 0.6],
    [3.01, 0.25, decay * 0.35],
    [4.2, 0.12, decay * 0.25],
  ]) {
    const osc = ctx.createOscillator()
    const env = ctx.createGain()
    osc.type = 'sine'
    osc.frequency.value = freq * mult
    env.gain.setValueAtTime(0, start)
    env.gain.linearRampToValueAtTime(level * amp, start + 0.005)
    env.gain.exponentialRampToValueAtTime(0.0001, start + d)
    osc.connect(env).connect(sfxOut)
    osc.start(start)
    osc.stop(start + d + 0.05)
  }
}

function sadNote(freq, start, dur, level, bend = 1) {
  const osc = ctx.createOscillator()
  const env = ctx.createGain()
  osc.type = 'triangle'
  osc.frequency.setValueAtTime(freq, start)
  if (bend !== 1) osc.frequency.exponentialRampToValueAtTime(freq * bend, start + dur)
  env.gain.setValueAtTime(0, start)
  env.gain.linearRampToValueAtTime(level, start + 0.02)
  env.gain.setValueAtTime(level, start + dur * 0.6)
  env.gain.exponentialRampToValueAtTime(0.0001, start + dur)
  osc.connect(env).connect(sfxOut)
  osc.start(start)
  osc.stop(start + dur + 0.05)
}

function canPlaySfx() {
  return musicState.sfx && ensureContext() && ctx.state === 'running'
}


export const sfx = {
  /** Bel "ding-dong" saat giliranmu tiba. */
  turn() {
    navigator.vibrate?.(120)
    if (!canPlaySfx()) return
    const t = ctx.currentTime + 0.02
    bellNote(midiHz(81), t, 0.32) // A5
    bellNote(midiHz(88), t + 0.18, 0.28) // E6
  },
  /** Nada turun "yah…" saat kamu langsung mati di gilirannya. */
  dead() {
    navigator.vibrate?.([80, 60, 180])
    if (!canPlaySfx()) return
    const t = ctx.currentTime + 0.02
    sadNote(midiHz(67), t, 0.28, 0.22) // G4
    sadNote(midiHz(63), t + 0.3, 0.28, 0.22) // Eb4
    sadNote(midiHz(60), t + 0.6, 0.75, 0.22, 0.94) // C4, sedikit melorot
  },
  toggle() {
    musicState.sfx = !musicState.sfx
    savePrefs()
    if (musicState.sfx) sfx.turn()
  },
}

export const music = {
  /** Mulai musik (dipanggil saat masuk meja permainan). */
  start() {
    if (!ensureContext()) return
    musicState.playing = true
    if (!timer) {
      nextBarTime = ctx.currentTime + 0.1
      tick()
      timer = setInterval(tick, 500)
    }
    resume().then(() => applyGain(2.5))
  },
  /** Hentikan musik dengan fade out (keluar dari meja). */
  stop() {
    musicState.playing = false
    musicState.blocked = false
    if (!ctx) return
    applyGain(1.2)
    setTimeout(() => {
      if (musicState.playing) return
      clearInterval(timer)
      timer = null
    }, 1300)
  },
  /**
   * Dipanggil pada setiap sentuhan/klik/tombol. Menyiapkan audio sejak interaksi pertama
   * (mis. saat menekan Buat room / Gabung / Mulai), supaya musik dan bel boleh berbunyi nanti.
   */
  unlock() {
    if (!ensureContext()) return
    if (ctx.state === 'running' && !musicState.blocked) return
    resume().then(() => {
      if (musicState.playing) applyGain(1.5)
    })
  },
  setVolume(v) {
    musicState.volume = v
    if (v > 0 && musicState.muted) musicState.muted = false
    savePrefs()
    applyGain(0.15)
  },
  /** Ganti lagu: nada yang sudah terjadwal dihentikan pelan, lalu lagu baru mulai. */
  setTrack(index) {
    musicState.track = ((index % TRACKS.length) + TRACKS.length) % TRACKS.length
    melodyPos = 2
    savePrefs()
    if (!ctx || !timer) return
    const now = ctx.currentTime
    master.gain.cancelScheduledValues(now)
    master.gain.setValueAtTime(master.gain.value, now)
    master.gain.linearRampToValueAtTime(0, now + 0.35)
    for (const osc of active) {
      try {
        osc.stop(now + 0.4)
      } catch {
        /* sudah berhenti */
      }
    }
    active.clear()
    tone.frequency.setValueAtTime(track().brightness, now + 0.4)
    barIndex = 0
    nextBarTime = now + 0.45
    tick()
    setTimeout(() => applyGain(1.2), 450)
  },
  nextTrack() {
    music.setTrack(musicState.track + 1)
  },
  toggleMute() {
    musicState.muted = !musicState.muted
    savePrefs()
    applyGain(0.3)
  },
}
