package id.perahu.game.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Aturan Perahu-Perahu yang tidak bergantung pada urutan giliran.
 * Kode aturan (R1..R16) mengikuti dokumen "Aturan Remi Perahu-Perahu".
 */
public final class Rules {

    public static final int MAX_CARDS_PER_MOVE = 5;   // R6
    public static final int MIN_NEW_PILE = 3;         // R5, R10
    public static final int DASAR_SET_SIZE = 5;       // R5
    public static final int MAX_POSITION = 14;        // As atas

    private Rules() {}

    /** Satu cara menaruh sekumpulan kartu (penting kalau ada joker). */
    public record Placement(Pile.Type type, Suit suit, int rank, List<Pile.Slot> slots, String label) {}

    // ---------------------------------------------------------------- urutan (R1)

    /** Coba taruh kartu tepat mengisi posisi from..to (inklusif) pada urutan satu jenis. */
    static Placement runInSpan(List<Card> cards, Suit suit, int from, int to, String prefix) {
        if (from < 1 || to > MAX_POSITION || to - from + 1 != cards.size()) return null;
        TreeSet<Integer> free = new TreeSet<>();
        for (int p = from; p <= to; p++) free.add(p);
        List<Pile.Slot> slots = new ArrayList<>();
        List<Card> aces = new ArrayList<>();
        List<Card> jokers = new ArrayList<>();
        for (Card c : cards) {
            if (c.isJoker()) { jokers.add(c); continue; }
            if (c.suit() != suit) return null;
            if (c.rank() == 1) { aces.add(c); continue; }
            if (!free.remove(c.rank())) return null;      // di luar rentang atau angka kembar
            slots.add(new Pile.Slot(c, c.rank()));
        }
        for (Card ace : aces) {
            if (free.remove(1)) slots.add(new Pile.Slot(ace, 1));
            else if (free.remove(14)) slots.add(new Pile.Slot(ace, 14));
            else return null;
        }
        List<String> jokerNotes = new ArrayList<>();
        for (Card j : jokers) {
            int p = free.pollFirst();
            slots.add(new Pile.Slot(j, p));
            jokerNotes.add("Joker = " + Card.rankLabel(p) + suit.symbol());
        }
        String label = prefix + " " + suit.symbol() + " " + Card.rankLabel(from) + "–" + Card.rankLabel(to)
                + (jokerNotes.isEmpty() ? "" : " (" + String.join(", ", jokerNotes) + ")");
        return new Placement(Pile.Type.RUN, suit, 0, slots, label);
    }

    static Suit commonSuit(List<Card> cards) {
        Suit suit = null;
        for (Card c : cards) {
            if (c.isJoker()) continue;
            if (suit == null) suit = c.suit();
            else if (suit != c.suit()) return null;
        }
        return suit;
    }

    static Integer commonRank(List<Card> cards) {
        Integer rank = null;
        for (Card c : cards) {
            if (c.isJoker()) continue;
            if (rank == null) rank = c.rank();
            else if (rank != c.rank()) return null;
        }
        return rank;
    }

    /** Semua cara sah menurunkan kartu sebagai urutan baru. */
    public static List<Placement> newRunPlacements(List<Card> cards) {
        List<Placement> out = new ArrayList<>();
        Suit suit = commonSuit(cards);
        if (suit == null) return out;                       // perlu minimal 1 kartu asli, satu jenis
        int n = cards.size();
        for (int from = 1; from + n - 1 <= MAX_POSITION; from++) {
            Placement p = runInSpan(cards, suit, from, from + n - 1, "Urutan");
            if (p != null) out.add(p);
        }
        return out;
    }

    /** Cara menurunkan kartu sebagai tumpukan sama angka baru (R2). */
    public static Placement newSetPlacement(List<Card> cards) {
        Integer rank = commonRank(cards);
        if (rank == null) return null;                      // perlu minimal 1 kartu asli
        List<Pile.Slot> slots = cards.stream().map(c -> new Pile.Slot(c, rank)).toList();
        boolean joker = cards.stream().anyMatch(Card::isJoker);
        return new Placement(Pile.Type.SET, null, rank, slots,
                "Sama angka " + Card.rankLabel(rank) + (joker ? " (Joker = " + Card.rankLabel(rank) + ")" : ""));
    }

    // ---------------------------------------------------------------- sambung (R9)

    /** Semua cara sah menyambung kartu ke tumpukan (tanpa cek R4 dan R12). */
    public static List<Placement> extensionPlacements(Pile pile, List<Card> cards) {
        List<Placement> out = new ArrayList<>();
        int n = cards.size();
        if (pile.type() == Pile.Type.RUN) {
            // R9: hanya satu ujung, naik ATAU turun
            Placement up = runInSpan(cards, pile.suit(), pile.high() + 1, pile.high() + n, "Sambung ke atas");
            Placement down = runInSpan(cards, pile.suit(), pile.low() - n, pile.low() - 1, "Sambung ke bawah");
            if (up != null) out.add(up);
            if (down != null) out.add(down);
        } else {
            for (Card c : cards) {
                if (!c.isJoker() && c.rank() != pile.rank()) return out;
            }
            List<Pile.Slot> slots = cards.stream().map(c -> new Pile.Slot(c, pile.rank())).toList();
            out.add(new Placement(Pile.Type.SET, null, pile.rank(), slots, "Sambung sama angka " + Card.rankLabel(pile.rank())));
        }
        return out;
    }

    /** R12: satu kartu X tidak boleh menyambung urutan selama ada tumpukan sama angka X yang terbuka. */
    public static boolean blockedByOpenSet(Pile target, List<Card> cards, List<Pile> table) {
        if (target.type() != Pile.Type.RUN || cards.size() != 1) return false;
        Card c = cards.get(0);
        if (c.isJoker()) return false;
        return table.stream().anyMatch(p -> p.type() == Pile.Type.SET && !p.closed() && p.rank() == c.rank());
    }

    public static boolean canExtendWith(Pile pile, Card card, List<Pile> table) {
        if (pile.closed()) return false;
        List<Card> one = List.of(card);
        return !extensionPlacements(pile, one).isEmpty() && !blockedByOpenSet(pile, one, table);
    }

    /**
     * Apakah ada sambungan sah. Cukup cek sambungan 1 kartu: kartu X yang ditolak R12
     * selalu bisa masuk ke tumpukan sama angka X yang masih terbuka.
     */
    public static boolean anyExtensionPossible(List<Card> hand, List<Pile> table) {
        for (Pile pile : table) {
            if (pile.closed()) continue;
            for (Card c : hand) {
                if (canExtendWith(pile, c, table)) return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- cari kombinasi di tangan

    private static Map<Suit, boolean[]> naturalPositions(List<Card> hand) {
        Map<Suit, boolean[]> pos = new HashMap<>();
        for (Card c : hand) {
            if (c.isJoker()) continue;
            boolean[] arr = pos.computeIfAbsent(c.suit(), s -> new boolean[MAX_POSITION + 2]);
            arr[c.rank()] = true;
            if (c.rank() == 1) arr[14] = true;
        }
        return pos;
    }

    /** Ada urutan minimal 3 kartu, dengan bantuan joker sebanyak jokers. */
    public static boolean hasRun(List<Card> hand, int jokers) {
        for (boolean[] arr : naturalPositions(hand).values()) {
            for (int from = 1; from + 2 <= MAX_POSITION; from++) {
                int have = 0;
                for (int p = from; p < from + 3; p++) if (arr[p]) have++;
                if (have >= 1 && 3 - have <= jokers) return true;
            }
        }
        return false;
    }

    public static boolean hasPureRun(List<Card> hand) {
        return hasRun(hand, 0);
    }

    /** Ada minimal size kartu sama angka (dengan bantuan joker). */
    public static boolean hasSet(List<Card> hand, int size, int jokers) {
        Map<Integer, Long> byRank = hand.stream().filter(c -> !c.isJoker())
                .collect(Collectors.groupingBy(Card::rank, Collectors.counting()));
        return byRank.values().stream().anyMatch(n -> n + jokers >= size);
    }

    public static long jokerCount(List<Card> hand) {
        return hand.stream().filter(Card::isJoker).count();
    }

    /** R11: apakah pemain punya langkah sah di awal gilirannya. */
    public static boolean hasLegalMove(List<Card> hand, boolean hasDasar, List<Pile> table) {
        if (!hasDasar) {
            return hasPureRun(hand) || hasSet(hand, DASAR_SET_SIZE, 0);
        }
        if (anyExtensionPossible(hand, table)) return true;
        int j = (int) jokerCount(hand);
        return hasRun(hand, j) || hasSet(hand, MIN_NEW_PILE, j);
    }
}
