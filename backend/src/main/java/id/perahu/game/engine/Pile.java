package id.perahu.game.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tumpukan di meja. RUN = urutan satu jenis (posisi 1..14, As bisa 1 atau 14).
 * SET = sama angka (rank tetap).
 */
public class Pile {

    public enum Type { RUN, SET }

    /** Kartu di tumpukan + nilai yang diwakilinya (posisi untuk RUN, rank untuk SET). */
    public record Slot(Card card, int value) {}

    private final String id;
    private final String ownerId;
    private final Type type;
    private final Suit suit;   // hanya RUN
    private final int rank;    // hanya SET
    private final List<Slot> slots = new ArrayList<>();
    private boolean closed;

    public Pile(String id, String ownerId, Type type, Suit suit, int rank, List<Slot> initial) {
        this.id = id;
        this.ownerId = ownerId;
        this.type = type;
        this.suit = suit;
        this.rank = rank;
        add(initial);
    }

    void add(List<Slot> more) {
        slots.addAll(more);
        slots.sort(Comparator.comparingInt(Slot::value));
    }

    void close() {
        this.closed = true;
    }

    public String id() { return id; }
    public String ownerId() { return ownerId; }
    public Type type() { return type; }
    public Suit suit() { return suit; }
    public int rank() { return rank; }
    public boolean closed() { return closed; }
    public List<Slot> slots() { return List.copyOf(slots); }

    public int low() {
        return slots.get(0).value();
    }

    public int high() {
        return slots.get(slots.size() - 1).value();
    }
}
