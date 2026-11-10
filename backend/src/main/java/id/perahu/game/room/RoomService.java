package id.perahu.game.room;

import id.perahu.game.engine.BotPlayer;
import id.perahu.game.engine.Card;
import id.perahu.game.engine.Game;
import id.perahu.game.engine.MoveException;
import id.perahu.game.engine.Pile;
import id.perahu.game.engine.PlayerState;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logika room tanpa login: buat room, gabung dengan kode + nama, masuk kembali dengan token.
 * Tidak bergantung pada Spring; pesan masuk/keluar berupa Map (diserialisasi ke JSON oleh handler).
 */
public class RoomService {

    /** Mengirim pesan ke satu sesi WebSocket. */
    public interface Sender {
        void send(String sessionId, Map<String, Object> message);
    }

    /**
     * @param turnTimeoutMillis batas waktu giliran untuk semua pemain; 0 = tanpa batas
     */
    public record Settings(int minPlayers, int maxPlayers, long offlineAutoPlayMillis,
                           long lobbyOfflineKickMillis, long idleRoomMillis, long turnTimeoutMillis,
                           long botDelayMillis) {}

    static final int CHAT_MAX_LENGTH = 200;
    static final int CHAT_BURST = 5;              // maksimal 5 pesan
    static final long CHAT_WINDOW_MILLIS = 5_000; // per 5 detik
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final List<String> BOT_NAMES = List.of(
            "Tojo", "Ampana", "Una Una", "Tete", "Ratolindo",
            "Ulubongka", "Togean", "Batudaka", "Walea", "Talatako");

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> sessionRoom = new ConcurrentHashMap<>();
    private final Sender sender;
    private final Settings settings;
    private final Random random = new SecureRandom();

    public RoomService(Sender sender, Settings settings) {
        this.sender = sender;
        this.settings = settings;
    }

    // ================================================================ pesan masuk

    public void handle(String sessionId, Map<String, Object> msg, long now) {
        String type = str(msg, "type");
        try {
            switch (type == null ? "" : type) {
                case "create" -> create(sessionId, str(msg, "name"), now);
                case "join" -> join(sessionId, str(msg, "code"), str(msg, "name"), now);
                case "rejoin" -> rejoin(sessionId, str(msg, "code"), str(msg, "token"), now);
                case "start" -> withMember(sessionId, (room, m) -> start(room, m));
                case "addBot" -> withMember(sessionId, this::addBot);
                case "removeBot" -> withMember(sessionId, (room, m) -> removeBot(room, m, str(msg, "playerId")));
                case "chat" -> withMember(sessionId, (room, m) -> chat(room, m, str(msg, "text"), now));
                case "play" -> withMember(sessionId, (room, m) -> play(room, m, msg));
                case "restart" -> withMember(sessionId, this::restart);
                case "leave" -> leave(sessionId, now);
                case "ping" -> sender.send(sessionId, Map.of("type", "pong"));
                default -> error(sessionId, "BAD_REQUEST", "Pesan tidak dikenal.");
            }
        } catch (MoveException e) {
            Map<String, Object> err = message("error");
            err.put("code", e.code());
            err.put("message", e.getMessage());
            if (!e.options().isEmpty()) err.put("options", e.options());
            sender.send(sessionId, err);
        }
    }

    public void disconnected(String sessionId, long now) {
        String code = sessionRoom.remove(sessionId);
        if (code == null) return;
        Room room = rooms.get(code);
        if (room == null) return;
        synchronized (room) {
            room.bySession(sessionId).ifPresent(m -> {
                m.sessionId = null;
                m.offlineSince = now;
            });
            broadcast(room);
        }
    }

    // ================================================================ room

    private void create(String sessionId, String rawName, long now) {
        String name = cleanName(rawName);
        leave(sessionId, now);
        Room room;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            room = new Room(sb.toString(), now);
        } while (rooms.putIfAbsent(room.code, room) != null);
        synchronized (room) {
            Room.Member m = addMember(room, name, sessionId);
            room.hostId = m.id;
            room.addChat(null, null, m.name + " membuat room.", now);
            joined(sessionId, room, m);
            broadcast(room);
        }
    }

    private void join(String sessionId, String rawCode, String rawName, long now) {
        String name = cleanName(rawName);
        Room room = findRoom(rawCode);
        leave(sessionId, now);
        synchronized (room) {
            if (!rooms.containsKey(room.code)) throw new MoveException("NO_ROOM", "Room tidak ditemukan.");
            if (!room.inLobby())
                throw new MoveException("GAME_RUNNING", "Permainan di room ini sedang berjalan. Tunggu sampai selesai.");
            if (room.members.size() >= settings.maxPlayers())
                throw new MoveException("ROOM_FULL", "Room sudah penuh (maksimal " + settings.maxPlayers() + " pemain).");
            boolean taken = room.members.values().stream().anyMatch(x -> x.name.equalsIgnoreCase(name));
            if (taken) throw new MoveException("NAME_TAKEN", "Nama \"" + name + "\" sudah dipakai di room ini.");
            Room.Member m = addMember(room, name, sessionId);
            room.lastActivity = now;
            room.addChat(null, null, m.name + " bergabung.", now);
            joined(sessionId, room, m);
            broadcast(room);
        }
    }

    private void rejoin(String sessionId, String rawCode, String token, long now) {
        Room room = rooms.get(rawCode == null ? "" : rawCode.trim().toUpperCase());
        if (room == null || token == null)
            throw new MoveException("REJOIN_FAILED", "Sesi sebelumnya sudah tidak ada.");
        synchronized (room) {
            Room.Member m = room.byToken(token)
                    .orElseThrow(() -> new MoveException("REJOIN_FAILED", "Sesi sebelumnya sudah tidak ada."));
            if (m.sessionId != null && !m.sessionId.equals(sessionId)) sessionRoom.remove(m.sessionId);
            m.sessionId = sessionId;
            sessionRoom.put(sessionId, room.code);
            room.lastActivity = now;
            joined(sessionId, room, m);
            broadcast(room);
        }
    }

    private void leave(String sessionId, long now) {
        String code = sessionRoom.remove(sessionId);
        if (code == null) return;
        Room room = rooms.get(code);
        if (room == null) return;
        synchronized (room) {
            Optional<Room.Member> m = room.bySession(sessionId);
            if (m.isEmpty()) return;
            room.addChat(null, null, m.get().name + " keluar.", now);
            if (room.inLobby()) {
                removeMember(room, m.get());
            } else {
                m.get().sessionId = null;     // di tengah permainan kursi tetap ada
                m.get().offlineSince = now;
            }
            if (room.hasHumans()) broadcast(room);
        }
    }

    private void chat(Room room, Room.Member m, String raw, long now) {
        String text = raw == null ? "" : raw.replaceAll("[\\p{Cntrl}&&[^\\n]]", "").replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) return;
        if (text.length() > CHAT_MAX_LENGTH)
            throw new MoveException("CHAT_TOO_LONG", "Pesan maksimal " + CHAT_MAX_LENGTH + " karakter.");
        while (!m.recentChat.isEmpty() && now - m.recentChat.peekFirst() > CHAT_WINDOW_MILLIS) m.recentChat.pollFirst();
        if (m.recentChat.size() >= CHAT_BURST)
            throw new MoveException("CHAT_SPAM", "Pelan-pelan, tunggu sebentar sebelum kirim pesan lagi.");
        m.recentChat.addLast(now);
        room.addChat(m.id, m.name, text, now);
        room.lastActivity = now;
        broadcast(room);
    }

    private void addBot(Room room, Room.Member m) {
        if (!m.id.equals(room.hostId)) throw new MoveException("NOT_HOST", "Hanya pembuat room yang bisa menambah bot.");
        if (!room.inLobby()) throw new MoveException("GAME_RUNNING", "Bot hanya bisa ditambah di lobby.");
        if (room.members.size() >= settings.maxPlayers())
            throw new MoveException("ROOM_FULL", "Room sudah penuh (maksimal " + settings.maxPlayers() + " pemain).");
        // nama acak yang belum dipakai di room ini
        List<String> free = new ArrayList<>(BOT_NAMES);
        free.removeIf(c -> room.members.values().stream().anyMatch(x -> x.name.equalsIgnoreCase(c)));
        String name = free.isEmpty() ? "Bot " + (room.members.size() + 1) : free.get(random.nextInt(free.size()));
        Room.Member bot = new Room.Member(UUID.randomUUID().toString(), name, UUID.randomUUID().toString(), true);
        room.members.put(bot.id, bot);
        room.addChat(null, null, "Bot " + name + " ikut bermain.", System.currentTimeMillis());
        broadcast(room);
    }

    private void removeBot(Room room, Room.Member m, String botId) {
        if (!m.id.equals(room.hostId)) throw new MoveException("NOT_HOST", "Hanya pembuat room yang bisa mengeluarkan bot.");
        if (!room.inLobby()) throw new MoveException("GAME_RUNNING", "Bot hanya bisa dikeluarkan di lobby.");
        Room.Member bot = botId == null ? null : room.members.get(botId);
        if (bot == null || !bot.bot) throw new MoveException("NO_PLAYER", "Bot tidak ditemukan.");
        room.members.remove(bot.id);
        broadcast(room);
    }

    private void start(Room room, Room.Member m) {
        if (!m.id.equals(room.hostId)) throw new MoveException("NOT_HOST", "Hanya pembuat room yang bisa memulai.");
        if (!room.inLobby()) throw new MoveException("GAME_RUNNING", "Permainan sudah berjalan.");
        int n = room.members.size();
        if (n < settings.minPlayers() || n > settings.maxPlayers())
            throw new MoveException("PLAYER_COUNT",
                    "Butuh " + settings.minPlayers() + "–" + settings.maxPlayers() + " pemain untuk mulai.");
        Map<String, String> names = new LinkedHashMap<>();
        room.members.values().forEach(x -> names.put(x.id, x.name));
        room.game = Game.start(names, random);
        room.gameNo++;
        room.addChat(null, null, "Permainan ke-" + room.gameNo + " dimulai. Selamat bermain!", System.currentTimeMillis());
        broadcast(room);
    }

    private void play(Room room, Room.Member m, Map<String, Object> msg) {
        if (room.game == null) throw new MoveException("NO_GAME", "Permainan belum dimulai.");
        Object rawIds = msg.get("cardIds");
        List<String> cardIds = new ArrayList<>();
        if (rawIds instanceof List<?> list) list.forEach(o -> cardIds.add(String.valueOf(o)));
        Object placement = msg.get("placement");
        Integer index = placement instanceof Number num ? num.intValue() : null;
        room.game.play(m.id, str(msg, "pileId"), cardIds, index);
        broadcast(room);
    }

    private void restart(Room room, Room.Member m) {
        if (!m.id.equals(room.hostId)) throw new MoveException("NOT_HOST", "Hanya pembuat room yang bisa memulai ulang.");
        if (room.game == null || room.game.phase() != Game.Phase.FINISHED)
            throw new MoveException("GAME_RUNNING", "Permainan belum selesai.");
        room.game = null;
        // pemain manusia yang offline dikeluarkan saat kembali ke lobby; bot tetap
        room.members.values().removeIf(x -> !x.bot && !x.connected());
        if (!room.members.containsKey(room.hostId)) room.firstHuman().ifPresent(h -> room.hostId = h.id);
        broadcast(room);
    }

    // ================================================================ tugas berkala

    /** Dipanggil berkala: jalankan giliran pemain offline, bersihkan lobby dan room kosong. */
    public void tick(long now) {
        for (Room room : rooms.values()) {
            synchronized (room) {
                boolean changed = false;
                if (room.inLobby()) {
                    changed = room.members.values().removeIf(
                            x -> !x.bot && !x.connected() && now - x.offlineSince > settings.lobbyOfflineKickMillis());
                    if (changed && !room.members.containsKey(room.hostId))
                        room.firstHuman().ifPresent(h -> room.hostId = h.id);
                } else if (room.game.phase() == Game.Phase.PLAYING) {
                    if (room.game.version() != room.seenVersion) {
                        room.seenVersion = room.game.version();
                        room.turnSince = now;
                    }
                    PlayerState cur = room.game.currentPlayer();
                    Room.Member mem = room.members.get(cur.id());
                    boolean botTurn = mem != null && mem.bot && now - room.turnSince >= settings.botDelayMillis();
                    boolean offlineTooLong = mem == null || (!mem.bot && !mem.connected()
                            && now - Math.max(mem.offlineSince, room.turnSince) > settings.offlineAutoPlayMillis());
                    boolean timedOut = settings.turnTimeoutMillis() > 0
                            && now - room.turnSince > settings.turnTimeoutMillis();
                    if (botTurn || offlineTooLong || timedOut) {
                        changed = BotPlayer.playAny(room.game, cur);
                    }
                }
                boolean everyoneGone = room.members.values().stream().noneMatch(Room.Member::connected);
                if (!room.hasHumans() || (everyoneGone && now - room.lastActivity > settings.idleRoomMillis())) {
                    rooms.remove(room.code);
                    continue;
                }
                if (room.members.values().stream().anyMatch(Room.Member::connected)) room.lastActivity = now;
                if (changed) broadcast(room);
            }
        }
    }

    public int roomCount() {
        return rooms.size();
    }

    // ================================================================ pembantu

    private interface MemberAction {
        void run(Room room, Room.Member member);
    }

    private void withMember(String sessionId, MemberAction action) {
        String code = sessionRoom.get(sessionId);
        Room room = code == null ? null : rooms.get(code);
        if (room == null) throw new MoveException("NO_ROOM", "Kamu belum masuk room.");
        synchronized (room) {
            Room.Member m = room.bySession(sessionId)
                    .orElseThrow(() -> new MoveException("NO_ROOM", "Kamu belum masuk room."));
            action.run(room, m);
        }
    }

    private Room findRoom(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase();
        Room room = rooms.get(code);
        if (room == null) throw new MoveException("NO_ROOM", "Room " + code + " tidak ditemukan.");
        return room;
    }

    private Room.Member addMember(Room room, String name, String sessionId) {
        Room.Member m = new Room.Member(UUID.randomUUID().toString(), name, UUID.randomUUID().toString(), false);
        m.sessionId = sessionId;
        room.members.put(m.id, m);
        sessionRoom.put(sessionId, room.code);
        return m;
    }

    private void removeMember(Room room, Room.Member m) {
        room.members.remove(m.id);
        if (m.id.equals(room.hostId)) room.firstHuman().ifPresent(h -> room.hostId = h.id);
        if (!room.hasHumans()) rooms.remove(room.code);   // room berisi bot saja ditutup
    }

    private static String cleanName(String raw) {
        String name = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) throw new MoveException("BAD_NAME", "Isi nama pemain dulu.");
        if (name.length() > 16) throw new MoveException("BAD_NAME", "Nama maksimal 16 karakter.");
        return name;
    }

    private void joined(String sessionId, Room room, Room.Member m) {
        Map<String, Object> msg = message("joined");
        msg.put("code", room.code);
        msg.put("playerId", m.id);
        msg.put("token", m.token);
        msg.put("name", m.name);
        sender.send(sessionId, msg);
    }

    private void error(String sessionId, String code, String text) {
        Map<String, Object> err = message("error");
        err.put("code", code);
        err.put("message", text);
        sender.send(sessionId, err);
    }

    private static String str(Map<String, Object> msg, String key) {
        Object v = msg.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static Map<String, Object> message(String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        return m;
    }

    // ================================================================ tampilan state per pemain

    private void broadcast(Room room) {
        for (Room.Member m : room.members.values()) {
            if (m.sessionId != null) sender.send(m.sessionId, stateFor(room, m));
        }
    }

    Map<String, Object> stateFor(Room room, Room.Member viewer) {
        Map<String, Object> msg = message("state");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", room.code);
        r.put("hostId", room.hostId);
        r.put("phase", room.game == null ? "LOBBY" : room.game.phase().name());
        r.put("minPlayers", settings.minPlayers());
        r.put("maxPlayers", settings.maxPlayers());
        List<Map<String, Object>> members = new ArrayList<>();
        for (Room.Member m : room.members.values()) {
            members.add(Map.of("id", m.id, "name", m.name, "connected", m.connected(), "bot", m.bot));
        }
        r.put("members", members);
        List<Map<String, Object>> chat = new ArrayList<>();
        for (Room.ChatMessage c : room.chat) {
            Map<String, Object> cv = new LinkedHashMap<>();
            cv.put("id", c.id());
            cv.put("playerId", c.playerId());
            cv.put("name", c.name());
            cv.put("text", c.text());
            cv.put("time", c.time());
            chat.add(cv);
        }
        r.put("chat", chat);
        msg.put("room", r);
        msg.put("you", Map.of("id", viewer.id, "name", viewer.name));
        msg.put("game", room.game == null ? null : gameView(room, viewer));
        return msg;
    }

    private Map<String, Object> gameView(Room room, Room.Member viewer) {
        Game g = room.game;
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("gameNo", room.gameNo);
        v.put("version", g.version());
        PlayerState cur = g.currentPlayer();
        v.put("turnPlayerId", cur == null ? null : cur.id());
        v.put("stockSize", g.stockSize());
        List<Map<String, Object>> players = new ArrayList<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (PlayerState p : g.players()) {
            names.put(p.id(), p.name());
            Map<String, Object> pv = new LinkedHashMap<>();
            pv.put("id", p.id());
            pv.put("name", p.name());
            pv.put("seat", p.seat());
            pv.put("handCount", p.hand().size());
            pv.put("hasDasar", p.hasDasar());
            pv.put("status", p.status().name());
            pv.put("deadOrder", p.deadOrder());
            Room.Member mem = room.members.get(p.id());
            pv.put("connected", mem != null && (mem.bot || mem.connected()));
            pv.put("bot", mem != null && mem.bot);
            players.add(pv);
        }
        v.put("players", players);

        List<Map<String, Object>> piles = new ArrayList<>();
        for (Pile pile : g.piles()) {
            Map<String, Object> pv = new LinkedHashMap<>();
            pv.put("id", pile.id());
            pv.put("ownerId", pile.ownerId());
            pv.put("ownerName", names.get(pile.ownerId()));
            pv.put("type", pile.type().name());
            pv.put("suit", pile.suit() == null ? null : pile.suit().name());
            pv.put("rank", pile.rank());
            pv.put("closed", pile.closed());
            List<Map<String, Object>> cards = new ArrayList<>();
            for (Pile.Slot s : pile.slots()) {
                Map<String, Object> cv = cardView(s.card());
                if (s.card().isJoker()) {
                    String as = pile.type() == Pile.Type.RUN
                            ? Card.rankLabel(s.value()) + pile.suit().symbol()
                            : Card.rankLabel(s.value());
                    cv.put("as", as);
                }
                cards.add(cv);
            }
            pv.put("cards", cards);
            piles.add(pv);
        }
        v.put("piles", piles);

        g.player(viewer.id).ifPresentOrElse(
                p -> v.put("hand", p.hand().stream().map(RoomService::cardView).toList()),
                () -> v.put("hand", List.of()));
        v.put("log", g.log());

        if (g.phase() == Game.Phase.FINISHED) {
            List<Map<String, Object>> results = new ArrayList<>();
            for (Game.Result res : g.results()) {
                Map<String, Object> rv = new LinkedHashMap<>();
                rv.put("playerId", res.playerId());
                rv.put("name", res.name());
                rv.put("place", res.place());
                rv.put("status", res.status().name());
                rv.put("deadOrder", res.deadOrder());
                rv.put("value", res.value());
                rv.put("cards", res.cardsLeft().stream().map(RoomService::cardView).toList());
                results.add(rv);
            }
            v.put("results", results);
        }
        return v;
    }

    private static Map<String, Object> cardView(Card c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id());
        m.put("suit", c.suit().name());
        m.put("rank", c.rank());
        m.put("label", c.label());
        return m;
    }
}
