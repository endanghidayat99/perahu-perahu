package id.perahu.game.engine;

import java.util.List;

/** Langkah ditolak. code = kode aturan (R1..R16) atau kode teknis. */
public class MoveException extends RuntimeException {

    private final String code;
    private final List<String> options;

    public MoveException(String code, String message) {
        this(code, message, List.of());
    }

    public MoveException(String code, String message, List<String> options) {
        super(message);
        this.code = code;
        this.options = options;
    }

    public String code() {
        return code;
    }

    /** Untuk code AMBIGUOUS: pilihan penempatan joker yang bisa dipilih pemain. */
    public List<String> options() {
        return options;
    }
}
