package id.perahu.game.engine;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pemain otomatis sederhana: mencari langkah sah pertama yang ditemukan.
 * Dipakai untuk simulasi/test dan untuk menjalankan giliran pemain yang terputus terlalu lama.
 */
public final class BotPlayer {

    private BotPlayer() {}

    /** @return true kalau berhasil melakukan satu langkah. */
    public static boolean playAny(Game g, PlayerState p) {
        List<Card> hand = p.hand();
        if (!p.hasDasar()) {
            if (tryCombos(g, p, hand, null)) return true;
            Map<Integer, List<Card>> byRank = hand.stream().filter(c -> !c.isJoker())
                    .collect(Collectors.groupingBy(Card::rank));
            for (List<Card> same : byRank.values()) {
                if (same.size() >= Rules.DASAR_SET_SIZE
                        && attempt(g, p, null, same.subList(0, Rules.DASAR_SET_SIZE))) return true;
            }
            return false;
        }
        for (Pile pile : g.piles()) {
            if (pile.closed()) continue;
            for (Card c : hand) {
                if (Rules.canExtendWith(pile, c, g.piles()) && attempt(g, p, pile.id(), List.of(c))) return true;
            }
        }
        return tryCombos(g, p, hand, null);
    }

    private static boolean tryCombos(Game g, PlayerState p, List<Card> hand, String pileId) {
        int n = hand.size();
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                for (int k = j + 1; k < n; k++)
                    if (attempt(g, p, pileId, List.of(hand.get(i), hand.get(j), hand.get(k)))) return true;
        return false;
    }

    private static boolean attempt(Game g, PlayerState p, String pileId, List<Card> cards) {
        List<String> ids = cards.stream().map(Card::id).toList();
        try {
            g.play(p.id(), pileId, ids, null);
            return true;
        } catch (MoveException e) {
            if ("AMBIGUOUS".equals(e.code())) {
                g.play(p.id(), pileId, ids, 0);
                return true;
            }
            return false;
        }
    }
}
