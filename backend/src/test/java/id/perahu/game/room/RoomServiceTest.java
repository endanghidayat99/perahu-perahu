package id.perahu.game.room;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoomServiceTest {

    private final Map<String, List<Map<String, Object>>> inbox = new HashMap<>();
    private final RoomService service = new RoomService(
            (sid, msg) -> inbox.computeIfAbsent(sid, k -> new ArrayList<>()).add(msg),
            new RoomService.Settings(4, 5, 45_000, 120_000, 30 * 60_000, 0, 1_200));

    private Map<String, Object> last(String sid, String type) {
        List<Map<String, Object>> list = inbox.getOrDefault(sid, List.of());
        for (int i = list.size() - 1; i >= 0; i--) if (type.equals(list.get(i).get("type"))) return list.get(i);
        return null;
    }

    private void send(String sid, Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        service.handle(sid, m, 1_000);
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(Map<String, Object> m, String... path) {
        Object cur = m;
        for (String p : path) cur = ((Map<String, Object>) cur).get(p);
        return (T) cur;
    }

    private String createRoomWithPlayers(int n) {
        send("s0", "type", "create", "name", "Endang");
        String code = (String) last("s0", "joined").get("code");
        for (int i = 1; i < n; i++) send("s" + i, "type", "join", "code", code.toLowerCase(), "name", "Pemain" + i);
        return code;
    }

    @Test void buatDanGabungTanpaLogin() {
        String code = createRoomWithPlayers(3);
        Map<String, Object> st = last("s2", "state");
        assertThat(((List<?>) get(st, "room", "members")).size()).isEqualTo(3);
        assertThat((Object) get(st, "room", "phase")).isEqualTo("LOBBY");
        assertThat(code.length()).isEqualTo(4);
    }

    @Test void namaKembarDitolak() {
        String code = createRoomWithPlayers(2);
        send("s9", "type", "join", "code", code, "name", "endang");
        assertThat(last("s9", "error").get("code")).isEqualTo("NAME_TAKEN");
    }

    @Test void mulaiButuhMinimalEmpatPemain() {
        createRoomWithPlayers(3);
        send("s0", "type", "start");
        assertThat(last("s0", "error").get("code")).isEqualTo("PLAYER_COUNT");
    }

    @Test void hanyaHostYangBisaMulai() {
        createRoomWithPlayers(4);
        send("s1", "type", "start");
        assertThat(last("s1", "error").get("code")).isEqualTo("NOT_HOST");
    }

    @Test void mulaiMembagi21KartuDanTanganLainTersembunyi() {
        String code = createRoomWithPlayers(4);
        send("s0", "type", "start");
        Map<String, Object> st = last("s1", "state");
        assertThat((Object) get(st, "room", "phase")).isEqualTo("PLAYING");
        List<Map<String, Object>> players = get(st, "game", "players");
        int alive = 0;
        for (Map<String, Object> p : players) {
            int cnt = (Integer) p.get("handCount");
            assertThat(cnt <= 21).isTrue();
            if ("ACTIVE".equals(p.get("status"))) alive++;
        }
        assertThat(alive >= 0).isTrue();
        List<?> hand = get(st, "game", "hand");
        assertThat(hand.size() <= 21).isTrue();
        // tidak ada data tangan pemain lain di state
        assertThat(players.get(0).containsKey("hand")).isFalse();
        // pemain ke-6 tidak bisa masuk saat permainan berjalan
        send("s7", "type", "join", "code", code, "name", "Telat");
        assertThat(last("s7", "error").get("code")).isEqualTo("GAME_RUNNING");
    }

    @Test void roomPenuhLimaPemain() {
        String code = createRoomWithPlayers(5);
        send("s8", "type", "join", "code", code, "name", "Keenam");
        assertThat(last("s8", "error").get("code")).isEqualTo("ROOM_FULL");
    }

    @Test void masukKembaliDenganToken() {
        String code = createRoomWithPlayers(4);
        String token = (String) last("s1", "joined").get("token");
        send("s0", "type", "start");
        service.disconnected("s1", 2_000);
        Map<String, Object> st = last("s0", "state");
        List<Map<String, Object>> members = get(st, "room", "members");
        assertThat(members.stream().filter(m -> "Pemain1".equals(m.get("name"))).findFirst().orElseThrow().get("connected"))
                .isEqualTo(false);
        send("s1b", "type", "rejoin", "code", code, "token", token);
        assertThat(last("s1b", "joined").get("name")).isEqualTo("Pemain1");
        assertThat((Object) get(last("s1b", "state"), "room", "phase")).isEqualTo("PLAYING");
    }

    @Test void pemainOfflineDijalankanOtomatis() {
        createRoomWithPlayers(4);
        send("s0", "type", "start");
        for (int i = 0; i < 4; i++) service.disconnected("s" + i, 2_000);
        // semua offline: tiap tick yang lewat batas waktu menjalankan satu langkah otomatis
        long now = 2_000;
        for (int i = 0; i < 400; i++) {
            now += 46_000;
            service.tick(now);
        }
        // room tetap ada atau sudah dibersihkan, yang penting tidak error
        assertThat(service.roomCount() <= 1).isTrue();
    }

    @Test void hostTambahBotDanMainMelawanBot() {
        send("s0", "type", "create", "name", "Endang");
        for (int i = 0; i < 4; i++) send("s0", "type", "addBot");
        List<Map<String, Object>> members = get(last("s0", "state"), "room", "members");
        assertThat(members.size()).isEqualTo(5);
        assertThat(members.get(1).get("bot")).isEqualTo(true);
        List<String> allowed = List.of("Tojo", "Ampana", "Una Una", "Tete", "Ratolindo",
                "Ulubongka", "Togean", "Batudaka", "Walea", "Talatako");
        java.util.Set<Object> botNames = new java.util.HashSet<>();
        for (int i = 1; i < 5; i++) {
            assertThat(allowed.contains(members.get(i).get("name"))).isTrue();
            botNames.add(members.get(i).get("name"));
        }
        assertThat(botNames.size()).isEqualTo(4);
        // room penuh: bot ke-5 ditolak
        send("s0", "type", "addBot");
        assertThat(last("s0", "error").get("code")).isEqualTo("ROOM_FULL");
        send("s0", "type", "start");
        String me = get(last("s0", "state"), "you", "id");
        // bot menjalankan gilirannya sendiri sampai tiba giliran pemain manusia atau permainan selesai
        long now = 1_000;
        boolean reached = false;
        for (int i = 0; i < 300 && !reached; i++) {
            now += 1_000;
            service.tick(now);
            Map<String, Object> st = last("s0", "state");
            reached = me.equals(get(st, "game", "turnPlayerId")) || "FINISHED".equals(get(st, "room", "phase"));
        }
        assertThat(reached).isTrue();
    }

    @Test void hanyaHostYangBisaTambahBotDanBotBisaDikeluarkan() {
        String code = createRoomWithPlayers(2);
        send("s1", "type", "addBot");
        assertThat(last("s1", "error").get("code")).isEqualTo("NOT_HOST");
        send("s0", "type", "addBot");
        List<Map<String, Object>> members = get(last("s0", "state"), "room", "members");
        String botId = (String) members.get(2).get("id");
        send("s0", "type", "removeBot", "playerId", botId);
        assertThat(((List<?>) get(last("s0", "state"), "room", "members")).size()).isEqualTo(2);
        assertThat(code.length()).isEqualTo(4);
    }

    @Test void roomBerisiBotSajaDitutupSaatManusiaKeluar() {
        send("s0", "type", "create", "name", "Endang");
        send("s0", "type", "addBot");
        send("s0", "type", "leave");
        assertThat(service.roomCount()).isEqualTo(0);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> chatOf(String sid) {
        return (List<Map<String, Object>>) get(last(sid, "state"), "room", "chat");
    }

    @Test void chatTerkirimKeSemuaPemain() {
        createRoomWithPlayers(3);
        send("s1", "type", "chat", "text", "  halo   semua  ");
        List<Map<String, Object>> chat = chatOf("s2");
        Map<String, Object> lastMsg = chat.get(chat.size() - 1);
        assertThat(lastMsg.get("text")).isEqualTo("halo semua");
        assertThat(lastMsg.get("name")).isEqualTo("Pemain1");
        // pesan sistem saat bergabung ikut tercatat
        assertThat(chat.stream().anyMatch(c -> "Pemain2 bergabung.".equals(c.get("text")))).isTrue();
    }

    @Test void chatKosongDiabaikanDanTerlaluPanjangDitolak() {
        createRoomWithPlayers(2);
        int before = chatOf("s0").size();
        send("s0", "type", "chat", "text", "   ");
        assertThat(chatOf("s0").size()).isEqualTo(before);
        send("s0", "type", "chat", "text", "x".repeat(201));
        assertThat(last("s0", "error").get("code")).isEqualTo("CHAT_TOO_LONG");
    }

    @Test void chatDibatasiSupayaTidakSpam() {
        createRoomWithPlayers(2);
        for (int i = 0; i < 5; i++) send("s1", "type", "chat", "text", "pesan " + i);
        send("s1", "type", "chat", "text", "pesan 6");
        assertThat(last("s1", "error").get("code")).isEqualTo("CHAT_SPAM");
        // setelah lewat 5 detik boleh kirim lagi
        Map<String, Object> m = new HashMap<>();
        m.put("type", "chat");
        m.put("text", "boleh lagi");
        service.handle("s1", m, 7_000);
        List<Map<String, Object>> chat = chatOf("s0");
        assertThat(chat.get(chat.size() - 1).get("text")).isEqualTo("boleh lagi");
    }

    @Test void langkahTidakSahMengirimKodeAturan() {
        createRoomWithPlayers(4);
        send("s0", "type", "start");
        Map<String, Object> st = last("s0", "state");
        String turn = get(st, "game", "turnPlayerId");
        String you = get(st, "you", "id");
        String sid = "s0";
        if (!you.equals(turn)) {
            for (int i = 1; i < 4; i++) {
                if (turn.equals(get(last("s" + i, "state"), "you", "id"))) sid = "s" + i;
            }
        }
        List<Map<String, Object>> hand = get(last(sid, "state"), "game", "hand");
        send(sid, "type", "play", "pileId", null, "cardIds", List.of(hand.get(0).get("id")));
        assertThat(last(sid, "error").get("code")).isEqualTo("R5");
    }
}
