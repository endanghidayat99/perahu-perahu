package id.perahu.game.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PlayerState {

    public enum Status { ACTIVE, DEAD, FINISHED }

    private final String id;
    private final String name;
    private final int seat;
    private final List<Card> hand = new ArrayList<>();
    private boolean hasDasar;
    private Status status = Status.ACTIVE;
    private int deadOrder;   // 1 = mati pertama; 0 = belum mati

    public PlayerState(String id, String name, int seat) {
        this.id = id;
        this.name = name;
        this.seat = seat;
    }

    public String id() { return id; }
    public String name() { return name; }
    public int seat() { return seat; }
    public boolean hasDasar() { return hasDasar; }
    public Status status() { return status; }
    public int deadOrder() { return deadOrder; }

    /** Kartu di tangan, diurutkan per jenis lalu angka (joker paling akhir). */
    public List<Card> hand() {
        List<Card> sorted = new ArrayList<>(hand);
        sorted.sort(Comparator.comparingInt((Card c) -> c.suit().ordinal()).thenComparingInt(Card::rank));
        return sorted;
    }

    List<Card> handRaw() { return hand; }

    public int handValue() {
        return hand.stream().mapToInt(Card::score).sum();
    }

    void markDasar() { hasDasar = true; }

    void die(int order) {
        status = Status.DEAD;
        deadOrder = order;
    }

    void finish() { status = Status.FINISHED; }
}
