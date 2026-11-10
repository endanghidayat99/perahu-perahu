package id.perahu.game.room;

import id.perahu.game.engine.Game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Satu room permainan. Semua akses wajib di dalam synchronized(room). */
public class Room {

    public static class Member {
        final String id;
        final String name;
        final String token;          // rahasia untuk masuk kembali tanpa login
        final boolean bot;           // dijalankan server, tanpa sesi
        String sessionId;            // null = offline (bot selalu null)
        long offlineSince;
        final Deque<Long> recentChat = new ArrayDeque<>();   // waktu kirim pesan terakhir (batas spam)

        Member(String id, String name, String token, boolean bot) {
            this.id = id;
            this.name = name;
            this.token = token;
            this.bot = bot;
        }

        /** Pemain manusia yang sedang tersambung. Bot tidak pernah dihitung tersambung. */
        public boolean connected() {
            return sessionId != null;
        }
    }

    final String code;
    final Map<String, Member> members = new LinkedHashMap<>();
    String hostId;
    Game game;
    long lastActivity;
    long seenVersion = -1;   // versi game terakhir yang dilihat tick()
    long turnSince;          // kapan giliran saat ini dimulai
    int gameNo;              // nomor permainan di room ini (naik setiap Mulai)

    /** Pesan chat. playerId null = pesan sistem (mis. "X bergabung"). */
    record ChatMessage(long id, String playerId, String name, String text, long time) {}

    static final int CHAT_HISTORY = 60;
    final List<ChatMessage> chat = new ArrayList<>();
    long chatSeq;

    void addChat(String playerId, String name, String text, long now) {
        chat.add(new ChatMessage(++chatSeq, playerId, name, text, now));
        if (chat.size() > CHAT_HISTORY) chat.remove(0);
    }

    Room(String code, long now) {
        this.code = code;
        this.lastActivity = now;
    }

    boolean inLobby() {
        return game == null;
    }

    boolean hasHumans() {
        return members.values().stream().anyMatch(m -> !m.bot);
    }

    Optional<Member> firstHuman() {
        return members.values().stream().filter(m -> !m.bot).findFirst();
    }

    Optional<Member> byToken(String token) {
        return members.values().stream().filter(m -> !m.bot && m.token.equals(token)).findFirst();
    }

    Optional<Member> bySession(String sessionId) {
        return members.values().stream().filter(m -> sessionId.equals(m.sessionId)).findFirst();
    }
}
