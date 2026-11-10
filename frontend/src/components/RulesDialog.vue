<template>
  <v-dialog :model-value="modelValue" max-width="520" scrollable @update:model-value="$emit('update:modelValue', $event)">
    <v-card title="Aturan singkat">
      <v-card-text class="rules">
        <p><b>Kartu:</b> 2 dek + 4 joker. Tiap pemain dapat 21 kartu, sisanya tidak dipakai.</p>
        <p><b>Dasar (langkah pertama):</b> minimal 3 kartu berurutan satu jenis, atau 5 kartu sama angka kalau tidak punya urutan. Dasar maksimal 5 kartu dan tanpa joker. Tidak punya dasar = langsung mati.</p>
        <p><b>Giliran:</b> satu aksi ke satu tumpukan, maksimal 5 kartu.</p>
        <ul>
          <li><b>Sambung</b> tumpukan siapa saja. Urutan harus satu jenis dan hanya di satu ujung (naik atau turun). As boleh di bawah (A-2-3) atau di atas (Q-K-A), tapi tidak memutar K-A-2.</li>
          <li><b>Tumpukan baru</b> (urutan atau sama angka, min. 3 kartu) boleh diturunkan kapan saja, walaupun masih ada yang bisa disambung.</li>
          <li>Tidak bisa keduanya = <b>mati</b>.</li>
        </ul>
        <p><b>Tris:</b> kalau ada tumpukan sama angka X yang terbuka, satu kartu X wajib masuk ke sana, tidak boleh menyambung urutan. Dua kartu atau lebih (mis. 7-8) boleh ke urutan.</p>
        <p><b>Joker:</b> bisa jadi kartu apa saja. Joker yang disambung ke tumpukan sama angka menutup tumpukan itu.</p>
        <p><b>Selesai murni:</b> begitu ada pemain yang menghabiskan semua kartunya, permainan langsung selesai walaupun pemain lain masih punya banyak kartu. Pemain itu juara, sisa kartu yang lain langsung dihitung. Kalau tidak ada yang habis, permainan selesai saat semua pemain mati.</p>
        <p>Nilai sisa: 2–10 sesuai angka, J/Q/K 10, As 15, Joker 25. Nilai terkecil menang; kalau seri, yang paling terakhir mati menang.</p>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn color="primary" @click="$emit('update:modelValue', false)">Tutup</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup>
defineProps({ modelValue: Boolean })
defineEmits(['update:modelValue'])
</script>

<style scoped>
.rules p,
.rules ul {
  margin-bottom: 10px;
  font-size: 14px;
}
.rules ul {
  padding-left: 18px;
}
</style>
