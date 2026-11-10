package id.perahu.game.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Satu permainan Perahu-Perahu. Semua aturan dicek di sini (server-authoritative).
 * Kelas ini tidak thread-safe: pemanggil wajib mengunci per room.
 */
public class Game {

    public static final int CARDS_PER_PLAYER = 21;

    public enum Phase { PLAYING, FINISHED }

    public record Result(String playerId, String name, int place, PlayerState.Status status,
                         int deadOrder, List<Card> cardsLeft, int value) {}

    private final List<PlayerState> players = new ArrayList<>();
    private final List<Pile> piles = new ArrayList<>();
    private final List<Card> stock = new ArrayList<>();
    private final List<String> log = new ArrayList<>();
    private Phase phase = Phase.PLAYING;
    private int turn;            // index ke players
    private int deadCounter;
    private int pileCounter;
    private long version;

    /** Mulai permainan baru: kocok, bagi 21 kartu, pilih pemain pertama secara acak. */
    public static Game start(Map<String, String> playerIdToName, Random random) {
        Game g = new Game();
        int seat = 0;
        for (Map.Entry<String, String> e : playerIdToName.entrySet()) {
            g.players.add(new PlayerState(e.getKey(), e.getValue(), seat++));
        }
        List<Card> deck = Card.twoDecks();
        Collections.shuffle(deck, random);
        for (PlayerState p : g.players) {
            for (int i = 0; i < CARDS_PER_PLAYER; i++) p.handRaw().add(deck.remove(deck.size() - 1));
        }
        g.stock.addAll(deck);
        g.turn = random.nextInt(g.players.size());
        g.log("Permainan dimulai. Giliran pertama: " + g.players.get(g.turn).name() + ".");
        g.beginTurn();
        return g;
    }

    /** Untuk test: susun tangan dan meja secara manual. */
    public static Game forTest(List<PlayerState> players) {
        Game g = new Game();
        g.players.addAll(players);
        return g;
    }

    // ---------------------------------------------------------------- akses

    public List<PlayerState> players() { return List.copyOf(players); }
    public List<Pile> piles() { return List.copyOf(piles); }
    public List<String> log() { return List.copyOf(log); }
    public Phase phase() { return phase; }
    public long version() { return version; }
    public int stockSize() { return stock.size(); }

    public PlayerState currentPlayer() {
        return phase == Phase.PLAYING ? players.get(turn) : null;
    }

    public Optional<PlayerState> player(String id) {
        return players.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    // ---------------------------------------------------------------- langkah

    /**
     * Pemain menurunkan kartu.
     *
     * @param pileId         null = dasar / tumpukan baru, selain itu = sambung ke tumpukan itu
     * @param cardIds        kartu dari tangan (1..5)
     * @param placementIndex pilihan penempatan joker bila ambigu (lihat MoveException AMBIGUOUS)
     */
    public void play(String playerId, String pileId, List<String> cardIds, Integer placementIndex) {
        if (phase != Phase.PLAYING) throw new MoveException("GAME_OVER", "Permainan sudah selesai.");
        PlayerState p = player(playerId).orElseThrow(() -> new MoveException("NO_PLAYER", "Pemain tidak dikenal."));
        if (currentPlayer() != p) throw new MoveException("NOT_YOUR_TURN", "Belum giliranmu.");
        if (cardIds == null || cardIds.isEmpty()) throw new MoveException("R6", "Pilih minimal 1 kartu.");
        if (cardIds.size() > Rules.MAX_CARDS_PER_MOVE)
            throw new MoveException("R6", "Maksimal 5 kartu sekali turun.");
        if (cardIds.stream().distinct().count() != cardIds.size())
            throw new MoveException("NOT_IN_HAND", "Kartu dipilih dua kali.");
        List<Card> cards = new ArrayList<>();
        for (String id : cardIds) {
            Card c = p.handRaw().stream().filter(h -> h.id().equals(id)).findFirst()
                    .orElseThrow(() -> new MoveException("NOT_IN_HAND", "Kartu tidak ada di tanganmu."));
            cards.add(c);
        }

        Rules.Placement placement;
        Pile target = null;
        if (!p.hasDasar()) {
            placement = choose(dasarPlacements(p, pileId, cards), placementIndex);
        } else if (pileId != null) {
            target = piles.stream().filter(x -> x.id().equals(pileId)).findFirst()
                    .orElseThrow(() -> new MoveException("NO_PILE", "Tumpukan tidak ditemukan."));
            placement = choose(extensionCandidates(target, cards), placementIndex);
        } else {
            placement = choose(newPileCandidates(p, cards), placementIndex);
        }

        // terapkan
        p.handRaw().removeAll(cards);
        String names = String.join(" ", cards.stream().map(Card::label).toList());
        if (target == null) {
            Pile pile = new Pile("p" + (++pileCounter), p.id(), placement.type(), placement.suit(),
                    placement.rank(), placement.slots());
            piles.add(pile);
            if (!p.hasDasar()) {
                p.markDasar();
                log(p.name() + " menurunkan dasar: " + names + ".");
            } else {
                log(p.name() + " menurunkan tumpukan baru: " + names + ".");
            }
        } else {
            target.add(placement.slots());
            boolean jokerAdded = cards.stream().anyMatch(Card::isJoker);
            if (target.type() == Pile.Type.SET && jokerAdded) {
                target.close();   // R4: joker lewat sambungan menutup tumpukan sama angka
                log(p.name() + " menyambung " + names + " dan menutup tumpukan " + describe(target) + ".");
            } else {
                log(p.name() + " menyambung " + names + " ke " + describe(target) + ".");
            }
        }

        if (p.handRaw().isEmpty()) {
            p.finish();
            // Selesai murni: satu pemain habis -> permainan langsung selesai, sisa kartu lain dihitung
            log("Selesai murni! " + p.name() + " menghabiskan semua kartu.");
            endGame();
            return;
        }
        advance();
    }

    private List<Rules.Placement> dasarPlacements(PlayerState p, String pileId, List<Card> cards) {
        if (pileId != null) throw new MoveException("R8", "Turunkan dasar dulu sebelum menyambung tumpukan.");
        if (cards.stream().anyMatch(Card::isJoker)) throw new MoveException("R5", "Dasar harus murni, tanpa joker.");
        if (cards.size() < Rules.MIN_NEW_PILE) throw new MoveException("R5", "Dasar minimal 3 kartu berurutan.");
        List<Rules.Placement> runs = Rules.newRunPlacements(cards);
        if (!runs.isEmpty()) return runs;
        Rules.Placement set = Rules.newSetPlacement(cards);
        if (set != null) {
            if (cards.size() != Rules.DASAR_SET_SIZE)
                throw new MoveException("R5", "Dasar sama angka harus tepat 5 kartu.");
            if (Rules.hasPureRun(p.handRaw()))
                throw new MoveException("R5", "Kamu punya kartu berurutan, dasar wajib memakai urutan.");
            return List.of(set);
        }
        throw new MoveException("R5", "Dasar harus urutan satu jenis (min. 3) atau 5 kartu sama angka.");
    }

    private List<Rules.Placement> extensionCandidates(Pile target, List<Card> cards) {
        if (target.closed()) throw new MoveException("R4", "Tumpukan ini sudah ditutup joker.");
        List<Rules.Placement> list = Rules.extensionPlacements(target, cards);
        if (list.isEmpty()) {
            throw target.type() == Pile.Type.RUN
                    ? new MoveException("R1", "Kartu tidak menyambung urutan ini (harus satu jenis, satu ujung, tanpa memutar K-A-2).")
                    : new MoveException("R2", "Tumpukan sama angka hanya menerima angka " + Card.rankLabel(target.rank()) + ".");
        }
        if (Rules.blockedByOpenSet(target, cards, piles)) {
            throw new MoveException("R12", "Ada tumpukan sama angka " + Card.rankLabel(cards.get(0).rank())
                    + " yang terbuka. Satu kartu itu harus masuk ke sana.");
        }
        return list;
    }

    /**
     * R10: tumpukan baru (urutan atau sama angka, min. 3 kartu) boleh diturunkan kapan saja,
     * walaupun masih ada kartu yang bisa disambung, sebagai strategi.
     */
    private List<Rules.Placement> newPileCandidates(PlayerState p, List<Card> cards) {
        if (cards.size() < Rules.MIN_NEW_PILE)
            throw new MoveException("R10", "Tumpukan baru minimal 3 kartu.");
        List<Rules.Placement> list = new ArrayList<>(Rules.newRunPlacements(cards));
        Rules.Placement set = Rules.newSetPlacement(cards);
        if (set != null) list.add(set);
        if (list.isEmpty())
            throw new MoveException("R10", "Tumpukan baru harus urutan satu jenis atau sama angka.");
        return list;
    }

    private Rules.Placement choose(List<Rules.Placement> options, Integer index) {
        if (options.size() == 1) return options.get(0);
        if (index == null || index < 0 || index >= options.size()) {
            throw new MoveException("AMBIGUOUS", "Pilih posisi joker.",
                    options.stream().map(Rules.Placement::label).toList());
        }
        return options.get(index);
    }

    // ---------------------------------------------------------------- giliran

    private void advance() {
        int n = players.size();
        for (int i = 1; i <= n; i++) {
            int next = (turn + i) % n;
            if (players.get(next).status() == PlayerState.Status.ACTIVE) {
                turn = next;
                beginTurn();
                return;
            }
        }
        endGame();
    }

    /** R7 / R11: pemain tanpa langkah sah langsung mati. */
    private void beginTurn() {
        version++;
        PlayerState p = players.get(turn);
        if (Rules.hasLegalMove(p.handRaw(), p.hasDasar(), piles)) return;
        p.die(++deadCounter);
        log(p.name() + (p.hasDasar() ? " tidak bisa jalan dan mati." : " tidak punya dasar dan mati."));
        advance();
    }

    private void endGame() {
        phase = Phase.FINISHED;
        version++;
        Result winner = results().get(0);
        log("Permainan selesai. Pemenang: " + winner.name() + ".");
    }

    /** R16: yang habis juara, lalu nilai sisa terkecil, seri -> yang paling terakhir mati. */
    public List<Result> results() {
        List<PlayerState> sorted = new ArrayList<>(players);
        sorted.sort(Comparator
                .comparing((PlayerState x) -> x.status() != PlayerState.Status.FINISHED)
                .thenComparingInt(PlayerState::handValue)
                .thenComparingInt(x -> -(x.status() == PlayerState.Status.DEAD ? x.deadOrder() : Integer.MAX_VALUE)));
        List<Result> out = new ArrayList<>();
        int place = 1;
        for (PlayerState p : sorted) {
            out.add(new Result(p.id(), p.name(), place++, p.status(), p.deadOrder(), p.hand(), p.handValue()));
        }
        return out;
    }

    private void log(String line) {
        log.add(line);
        if (log.size() > 50) log.remove(0);
    }

    private static String describe(Pile pile) {
        return pile.type() == Pile.Type.RUN
                ? "urutan " + pile.suit().symbol() + " " + Card.rankLabel(pile.low()) + "–" + Card.rankLabel(pile.high())
                : "sama angka " + Card.rankLabel(pile.rank());
    }

    // ---------------------------------------------------------------- khusus test

    void testSetTurn(String playerId) {
        for (int i = 0; i < players.size(); i++) if (players.get(i).id().equals(playerId)) turn = i;
    }

    void testAddPile(Pile pile) {
        piles.add(pile);
        pileCounter++;
    }

    static Map<String, String> names(String... idName) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < idName.length; i += 2) m.put(idName[i], idName[i + 1]);
        return m;
    }
}
