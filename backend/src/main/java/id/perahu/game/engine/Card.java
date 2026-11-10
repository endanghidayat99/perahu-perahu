package id.perahu.game.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Satu kartu. rank: 1 = As, 2..10, 11 = J, 12 = Q, 13 = K, 0 = Joker.
 * id unik karena permainan memakai 2 dek (mis. "H7-a", "H7-b", "JK-3").
 */
public record Card(String id, Suit suit, int rank) {

    public boolean isJoker() {
        return suit == Suit.JOKER;
    }

    /** Nilai sisa kartu di akhir permainan (R15). */
    public int score() {
        if (isJoker()) return 25;
        if (rank == 1) return 15;
        if (rank >= 11) return 10;
        return rank;
    }

    public String label() {
        return isJoker() ? "Joker" : rankLabel(rank) + suit.symbol();
    }

    public static String rankLabel(int rankOrPosition) {
        return switch (rankOrPosition) {
            case 1, 14 -> "A";
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            default -> String.valueOf(rankOrPosition);
        };
    }

    /** 2 dek x 52 kartu + 4 joker = 108 kartu. */
    public static List<Card> twoDecks() {
        List<Card> deck = new ArrayList<>(108);
        for (String d : new String[]{"a", "b"}) {
            for (Suit s : Suit.NATURAL) {
                for (int r = 1; r <= 13; r++) {
                    deck.add(new Card(s.name() + rankLabel(r) + "-" + d, s, r));
                }
            }
        }
        for (int i = 1; i <= 4; i++) deck.add(new Card("JK-" + i, Suit.JOKER, 0));
        return deck;
    }

    /** Pembuat kartu untuk test, mis. of("H7"), of("SA"), of("C10"), of("JK"). */
    public static Card of(String code, String deckTag) {
        if (code.startsWith("JK")) return new Card("JK-" + deckTag, Suit.JOKER, 0);
        Suit s = Suit.valueOf(code.substring(0, 1));
        String r = code.substring(1);
        int rank = switch (r) {
            case "A" -> 1;
            case "J" -> 11;
            case "Q" -> 12;
            case "K" -> 13;
            default -> Integer.parseInt(r);
        };
        return new Card(s.name() + r + "-" + deckTag, s, rank);
    }
}
