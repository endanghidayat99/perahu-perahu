# Perahu-Perahu — game remi online (web)

Game kartu remi khas Sulawesi Tengah untuk 4–5 pemain. Pemain cukup isi nama, lalu buat atau gabung room dengan kode 4 huruf, tanpa login.

- **Backend:** Spring Boot 3 (Java 21) + WebSocket. Semua aturan dicek di server, dan setiap pemain hanya menerima kartunya sendiri.
- **Frontend:** Vue 3 + Vuetify 3 (Vite). Hasil build disajikan langsung oleh Spring Boot, jadi cukup satu file jar.

## Struktur

```
backend/
  src/main/java/id/perahu/game/
    engine/   aturan permainan (R1–R16), tanpa Spring
    room/     room, gabung tanpa login, masuk kembali, langkah otomatis
    web/      endpoint WebSocket /ws
  src/main/resources/static/   hasil build frontend (sudah ada di zip)
  src/test/                    test aturan (contoh kasus 1–20) + simulasi permainan penuh
frontend/
  src/views/       Home, Lobby, Game
  src/components/  kartu, dialog aturan, dialog hasil
  src/store.js     koneksi WebSocket + state
```

## Menjalankan

Folder `backend/src/main/resources/static` sudah berisi hasil build frontend. Jadi untuk langsung main:

```bash
cd backend
mvn spring-boot:run
# buka http://localhost:8080
```

Supaya bisa mencoba sendiri dengan 2 tab browser (minimal pemain jadi 2):

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--game.min-players=2
```

Sesi pemain disimpan per tab browser. Jadi tiap tab dihitung sebagai pemain berbeda, dan refresh tidak membuat pemain keluar dari room.

### Development frontend

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173, /ws diteruskan ke backend :8080
npm run build      # menulis ulang backend/src/main/resources/static
```

### Build jar saja

```bash
cd backend && mvn package
java -jar target/perahu-perahu.jar
```

## Build rilis & deploy ke server (Docker)

Prasyarat mesin build: Docker (buildx) dan akses ke Docker Hub, Maven Central, npm registry
(atau mirror internal lewat `MAVEN_MIRROR_URL` / `NPM_REGISTRY`). Server cukup Docker + Compose v2, tanpa internet.

```bash
# di mesin pengembang: build image + kemas -> dist/perahu-perahu-<VERSION>.tar.gz
./deploy/build.sh
./deploy/build.sh 1.1.0

# atau sekaligus salin ke server dan deploy (lewat scp + ssh, butuh sudo di server)
./deploy/build.sh 1.1.0 root@10.0.0.5              # tujuan bawaan /opt/perahu-perahu
./deploy/build.sh 1.1.0 user@server:/srv/perahu-perahu

# deploy manual di server
tar -xzf perahu-perahu-1.1.0.tar.gz && sudo ./perahu-perahu-1.1.0/deploy.sh

# kembali ke versi sebelumnya
sudo /opt/perahu-perahu/deploy.sh --rollback 1.0.0
```

- Versi bawaan diambil dari berkas `VERSION`. Image dibangun untuk `linux/amd64`. Untuk server ARM (`uname -m` = `aarch64`) atau mencoba di Mac Apple Silicon, pakai `PLATFORM=linux/arm64 ./deploy/build.sh ...`. `deploy.sh` menolak image yang arsitekturnya tidak cocok dengan server (gejala: `exec format error`). Folder di server bisa diubah dengan `APP_DIR=...`.
- Test backend ikut dijalankan saat build image. Kalau ada test yang gagal, build berhenti.
- `deploy.sh` memverifikasi checksum, memuat image, memasang berkas ke `APP_DIR`, memperbarui `APP_VERSION` di `.env`, lalu menunggu aplikasi sehat. Kalau gagal, log dan perintah rollback ditampilkan.
- Deploy pertama membuat `.env` dari `.env.example`. Port bawaannya **8090**, supaya tidak bentrok dengan aplikasi lain di 8080. Pengaturan permainan ada di `.env` (`GAME_MIN_PLAYERS`, `GAME_TURN_TIMEOUT_SECONDS`, dan lainnya). `.env` tidak pernah ditimpa.
- Operasional sehari-hari cukup dengan `docker compose` di `APP_DIR` (`ps`, `logs -f app`, `restart app`).
- State room disimpan di memori. Restart atau deploy ulang akan menghentikan permainan yang sedang berjalan.

Kalau dipasang di belakang nginx atau Cloudflare, teruskan header `Upgrade`/`Connection` untuk path `/ws`. Contoh nginx:

```nginx
location /ws {
    proxy_pass http://127.0.0.1:8090;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 3600s;
}
location / { proxy_pass http://127.0.0.1:8090; }
```

## Pengaturan (`application.properties`)

| Properti | Bawaan | Arti |
| --- | --- | --- |
| `game.min-players` / `game.max-players` | 4 / 5 | Jumlah pemain per room |
| `game.offline-autoplay-seconds` | 45 | Pemain offline saat gilirannya dijalankan otomatis setelah sekian detik |
| `game.turn-timeout-seconds` | 0 | Batas waktu giliran untuk semua pemain (0 = tanpa batas) |
| `game.bot-delay-millis` | 1200 | Jeda sebelum bot melangkah, supaya langkahnya bisa diikuti di layar |
| `game.lobby-offline-kick-seconds` | 120 | Pemain offline di lobby dikeluarkan |
| `game.idle-room-minutes` | 30 | Room tanpa pemain online dihapus |
| `game.allowed-origins` | `*` | Origin yang boleh membuka WebSocket. Isi dengan domainmu di produksi |

Di Docker, semua properti ini diatur lewat `.env` dengan nama huruf besar, mis. `game.min-players` menjadi `GAME_MIN_PLAYERS`.

Langkah otomatis memakai `BotPlayer`: bot menjalankan langkah sah pertama yang ditemukannya.

## Alur permainan di layar

1. Pembuat room mengisi nama, lalu menekan **Buat room baru**. Kode room dan tombol **Bagikan link** muncul. Kursi kosong bisa diisi lewat **Tambah bot**, jadi kamu bisa main sendiri melawan 3–4 bot. Bot bisa dikeluarkan lagi dengan tombol ×.
2. Teman membuka link (kode room sudah terisi), mengisi nama, lalu menekan **Gabung**.
3. Setelah 4–5 pemain masuk, pembuat room menekan **Mulai**. Setiap pemain dapat 21 kartu.
4. Saat giliranmu: ketuk kartu untuk memilih (maksimal 5).
   - Untuk menyambung, ketuk tumpukan di meja yang ingin disambung.
   - Untuk dasar atau tumpukan baru, tekan **Turunkan dasar** / **Tumpukan baru**.
   - Kalau posisi joker bisa lebih dari satu, muncul pilihan posisinya.
5. Langkah yang tidak sah ditolak dengan pesan dan kode aturannya (mis. `R12`).
6. Pemain tanpa langkah sah otomatis mati. Di akhir permainan muncul peringkat dan nilai sisa kartu. Pembuat room bisa menekan **Main lagi**.

## Protokol WebSocket (`/ws`, JSON)

| Arah | `type` | Isi |
| --- | --- | --- |
| klien → server | `create` | `name` |
| klien → server | `join` | `code`, `name` |
| klien → server | `rejoin` | `code`, `token` (otomatis saat tersambung ulang) |
| klien → server | `start` / `restart` / `leave` / `addBot` | — |
| klien → server | `removeBot` | `playerId` |
| klien → server | `play` | `pileId` (null = dasar/tumpukan baru), `cardIds`, `placement` (opsional) |
| server → klien | `joined` | `code`, `playerId`, `token`, `name` |
| server → klien | `state` | `room`, `you`, `game` (termasuk `hand` milikmu saja, `results` saat selesai) |
| server → klien | `error` | `code`, `message`, `options` (untuk `AMBIGUOUS`) |
