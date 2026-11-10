package id.perahu.game.engine;

public enum Suit {
    S("♠"), H("♥"), D("♦"), C("♣"), JOKER("★");

    public static final Suit[] NATURAL = {S, H, D, C};

    private final String symbol;

    Suit(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
