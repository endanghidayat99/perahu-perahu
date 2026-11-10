<template>
  <div
    class="pcard no-select"
    :class="{ red, joker: isJoker, selected, small, compact, clickable }"
    :title="as ? `Joker = ${as}` : card.label"
  >
    <template v-if="isJoker && as">
      <!-- joker di meja: pojok menunjukkan kartu yang diwakili, supaya tetap terbaca saat bertumpuk -->
      <div class="corner">{{ asRank }}<br />{{ asSuit }}</div>
      <div class="center">★</div>
    </template>
    <template v-else-if="isJoker">
      <div class="corner">JKR</div>
      <div class="center">★</div>
    </template>
    <template v-else>
      <div class="corner">{{ rankText }}<br />{{ symbol }}</div>
      <div class="center">{{ symbol }}</div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  card: { type: Object, required: true },
  selected: Boolean,
  small: Boolean,
  compact: Boolean, // kartu tangan di layar HP
  clickable: Boolean,
})

const SYMBOLS = { S: '♠', H: '♥', D: '♦', C: '♣' }
const isJoker = computed(() => props.card.suit === 'JOKER')
const red = computed(() => props.card.suit === 'H' || props.card.suit === 'D')
const symbol = computed(() => SYMBOLS[props.card.suit])
const as = computed(() => props.card.as)
// "as" dari server, mis. "9♠" (urutan) atau "7" (sama angka)
const asSuit = computed(() => (/[♠♥♦♣]$/.test(as.value || '') ? as.value.slice(-1) : '★'))
const asRank = computed(() => (/[♠♥♦♣]$/.test(as.value || '') ? as.value.slice(0, -1) : as.value))
const rankText = computed(() => {
  const r = props.card.rank
  return { 1: 'A', 11: 'J', 12: 'Q', 13: 'K' }[r] || String(r)
})
</script>

<style scoped>
.pcard {
  position: relative;
  width: 56px;
  height: 80px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #cbd5e1;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.25);
  color: #111827;
  flex: 0 0 auto;
  transition: transform 0.12s ease, box-shadow 0.12s ease;
}
.pcard.compact {
  width: 46px;
  height: 68px;
  border-radius: 7px;
}
.compact .corner {
  font-size: 13px;
  left: 4px;
}
.compact .center {
  font-size: 25px;
}
.pcard.small {
  width: 40px;
  height: 58px;
  border-radius: 6px;
}
.pcard.red {
  color: #dc2626;
}
.pcard.joker {
  color: #7c3aed;
  background: #faf5ff;
}
.pcard.clickable {
  cursor: pointer;
}
.pcard.selected {
  transform: translateY(-14px);
  box-shadow: 0 0 0 3px #f59e0b, 0 6px 12px rgba(0, 0, 0, 0.35);
}
.corner {
  position: absolute;
  top: 3px;
  left: 5px;
  font-weight: 700;
  font-size: 14px;
  line-height: 1.05;
  text-align: center;
}
.small .corner {
  font-size: 11px;
  left: 3px;
  top: 2px;
}
.center {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
  padding-top: 10px;
}
.small .center {
  font-size: 20px;
  padding-top: 8px;
}
</style>
