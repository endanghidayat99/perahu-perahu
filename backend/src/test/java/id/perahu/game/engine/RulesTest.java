package id.perahu.game.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contoh kasus 1–20 dari dokumen aturan + perhitungan skor + simulasi permainan penuh. */
class RulesTest {

    private static final AtomicInteger TAG = new AtomicInteger();

    private static List<Card> cards(String... codes) {
        return Arrays.stream(codes).map(c -> Card.of(c, "t" + TAG.incrementAndGet())).collect(Collectors.toList());
    }

    private static PlayerState player(String id, boolean dasar, List<Card> hand) {
        PlayerState p = new PlayerState(id, id.toUpperCase(), id.charAt(0) - 'a');
        p.handRaw().addAll(hand);
        if (dasar) p.markDasar();
        return p;
    }

    private static Pile run(String id, String... codes) {
        Rules.Placement pl = Rules.newRunPlacements(cards(codes)).get(0);
        return new Pile(id, "b", Pile.Type.RUN, pl.suit(), 0, pl.slots());
    }

    private static Pile set(String id, boolean closed, String... codes) {
        Rules.Placement pl = Rules.newSetPlacement(cards(codes));
        Pile p = new Pile(id, "b", Pile.Type.SET, null, pl.rank(), pl.slots());
        if (closed) p.close();
        return p;
    }

    /** Game dengan pemain "a" (giliran) dan "b" (punya kartu supaya permainan tidak selesai). */
    private static Game game(PlayerState a, Pile... table) {
        PlayerState b = player("b", true, cards("C2", "D4", "S9", "HK"));
        Game g = Game.forTest(List.of(a, b));
        for (Pile p : table) g.testAddPile(p);
        g.testSetTurn("a");
        return g;
    }

    private static List<String> ids(PlayerState p, String... codesInOrder) {
        List<String> out = new ArrayList<>();
        List<Card> pool = new ArrayList<>(p.handRaw());
        for (String code : codesInOrder) {
            Card want = Card.of(code, "x");
            Card found = pool.stream().filter(c -> c.suit() == want.suit() && c.rank() == want.rank())
                    .findFirst().orElseThrow();
            pool.remove(found);
            out.add(found.id());
        }
        return out;
    }

    private static void expectError(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(MoveException.class)
                .satisfies(e -> assertThat(((MoveException) e).code()).isEqualTo(code));
    }

    // ------------------------------------------------------------- dasar

    @Test void kasus01_dasarUrutan() {
        PlayerState a = player("a", false, cards("H5", "H6", "H7", "C2"));
        Game g = game(a);
        g.play("a", null, ids(a, "H5", "H6", "H7"), null);
        assertThat(a.hasDasar()).isTrue();
        assertThat(g.piles()).hasSize(1);
    }

    @Test void kasus02_dasarSamaAngkaDitolakKarenaPunyaUrutan() {
        PlayerState a = player("a", false, cards("S8", "S9", "S10", "C9", "H9", "D9", "S9", "H9"));
        Game g = game(a);
        List<String> nines = ids(a, "C9", "H9", "D9", "S9", "H9");
        expectError(() -> g.play("a", null, nines, null), "R5");
    }

    @Test void kasus03_dasarLimaSamaAngkaTanpaUrutan() {
        PlayerState a = player("a", false, cards("SK", "HK", "DK", "CK", "SK", "H2", "D7"));
        Game g = game(a);
        g.play("a", null, ids(a, "SK", "HK", "DK", "CK", "SK"), null);
        assertThat(g.piles().get(0).type()).isEqualTo(Pile.Type.SET);
    }

    @Test void kasus04_dasarLebihDariLimaKartu() {
        PlayerState a = player("a", false, cards("D3", "D4", "D5", "D6", "D7", "D8"));
        Game g = game(a);
        expectError(() -> g.play("a", null, ids(a, "D3", "D4", "D5", "D6", "D7", "D8"), null), "R6");
    }

    @Test void kasus05_dasarTidakBolehJoker() {
        PlayerState a = player("a", false, cards("H5", "JK", "H7", "H8", "H9"));
        Game g = game(a);
        expectError(() -> g.play("a", null, ids(a, "H5", "JK", "H7"), null), "R5");
    }

    // ------------------------------------------------------------- urutan & As

    @Test void kasus06_sambungAsDiAtas() {
        PlayerState a = player("a", true, cards("CA", "D2"));
        Game g = game(a, run("p1", "CJ", "CQ", "CK"));
        g.play("a", "p1", ids(a, "CA"), null);
        assertThat(g.piles().get(0).high()).isEqualTo(14);
    }

    @Test void kasus07_tidakBolehMemutar() {
        PlayerState a = player("a", true, cards("C2", "D9"));
        Game g = game(a, run("p1", "CQ", "CK", "CA"));
        expectError(() -> g.play("a", "p1", ids(a, "C2"), null), "R1");
    }

    @Test void kasus08_sambungAsDiBawah() {
        PlayerState a = player("a", true, cards("HA", "D9"));
        Game g = game(a, run("p1", "H2", "H3", "H4"));
        g.play("a", "p1", ids(a, "HA"), null);
        assertThat(g.piles().get(0).low()).isEqualTo(1);
    }

    @Test void kasus09_jokerMelengkapiUrutan() {
        PlayerState a = player("a", true, cards("S7", "JK", "S9", "D2"));
        Game g = game(a, run("p1", "S4", "S5", "S6"));
        g.play("a", "p1", ids(a, "S7", "JK", "S9"), null);
        Pile p = g.piles().get(0);
        assertThat(p.high()).isEqualTo(9);
        assertThat(p.slots().stream().filter(s -> s.card().isJoker()).findFirst().orElseThrow().value()).isEqualTo(8);
    }

    @Test void kasus10_sambungDuaUjungDitolak() {
        PlayerState a = player("a", true, cards("C4", "C8", "D2"));
        Game g = game(a, run("p1", "C5", "C6", "C7"));
        expectError(() -> g.play("a", "p1", ids(a, "C4", "C8"), null), "R1");
    }

    // ------------------------------------------------------------- tris (R12, R13)

    @Test void kasus11_satuKartuHarusKeTris() {
        PlayerState a = player("a", true, cards("S7", "D2"));
        Game g = game(a, run("p1", "S4", "S5", "S6"), set("p2", false, "H7", "D7", "C7"));
        expectError(() -> g.play("a", "p1", ids(a, "S7"), null), "R12");
    }

    @Test void kasus12_duaKartuBolehKeUrutan() {
        PlayerState a = player("a", true, cards("S7", "S8", "D2"));
        Game g = game(a, run("p1", "S4", "S5", "S6"), set("p2", false, "H7", "D7", "C7"));
        g.play("a", "p1", ids(a, "S7", "S8"), null);
        assertThat(g.piles().get(0).high()).isEqualTo(8);
    }

    @Test void kasus13_laranganTrisBerlakuKeBawah() {
        PlayerState a = player("a", true, cards("D7", "S2"));
        Game g = game(a, run("p1", "D8", "D9", "D10"), set("p2", false, "H7", "S7", "C7"));
        expectError(() -> g.play("a", "p1", ids(a, "D7"), null), "R12");
    }

    @Test void kasus14_sambungKeTris() {
        PlayerState a = player("a", true, cards("C7", "D2"));
        Game g = game(a, set("p1", false, "H7", "D7", "S7"));
        g.play("a", "p1", ids(a, "C7"), null);
        assertThat(g.piles().get(0).slots()).hasSize(4);
    }

    @Test void kasus15_jokerMenutupTris() {
        PlayerState a = player("a", true, cards("JK", "D2"));
        Game g = game(a, set("p1", false, "H9", "D9", "S9"));
        g.play("a", "p1", ids(a, "JK"), null);
        assertThat(g.piles().get(0).closed()).isTrue();
    }

    @Test void kasus16_trisTertutupMenolakKartu() {
        PlayerState a = player("a", true, cards("C7", "D2"));
        Game g = game(a, set("p1", true, "H7", "D7", "JK"));
        expectError(() -> g.play("a", "p1", ids(a, "C7"), null), "R4");
    }

    @Test void kasus17_jokerSaatTurunTidakMenutup() {
        PlayerState a = player("a", true, cards("D9", "S2"));
        Game g = game(a, set("p1", false, "H9", "S9", "JK"));
        g.play("a", "p1", ids(a, "D9"), null);
        assertThat(g.piles().get(0).closed()).isFalse();
    }

    @Test void kasus18_urutanBaruBolehWalauMasihBisaSambung() {
        PlayerState a = player("a", true, cards("H8", "CJ", "CQ", "CK"));
        Game g = game(a, run("p1", "H5", "H6", "H7"));
        g.play("a", null, ids(a, "CJ", "CQ", "CK"), null);
        assertThat(g.piles()).hasSize(2);
    }

    @Test void tumpukanBaruTetapMinimalTigaKartu() {
        PlayerState a = player("a", true, cards("CJ", "CQ", "D2"));
        Game g = game(a);
        expectError(() -> g.play("a", null, ids(a, "CJ", "CQ"), null), "R10");
    }

    @Test void kasus19_trisBolehWalauMasihBisaSambung() {
        PlayerState a = player("a", true, cards("H8", "C4", "D4", "S4", "D2"));
        Game g = game(a, run("p1", "H5", "H6", "H7"));
        g.play("a", null, ids(a, "C4", "D4", "S4"), null);
        assertThat(g.piles().get(1).type()).isEqualTo(Pile.Type.SET);
    }

    @Test void trisDenganJokerBolehWalauMasihBisaSambung() {
        PlayerState a = player("a", true, cards("H8", "C4", "D4", "JK", "D2"));
        Game g = game(a, run("p1", "H5", "H6", "H7"));
        g.play("a", null, ids(a, "C4", "D4", "JK"), null);
        assertThat(g.piles().get(1).closed()).isFalse();
    }

    @Test void kasus20_belumDasarTidakBolehSambung() {
        PlayerState a = player("a", false, cards("H8", "S3", "S4", "S5"));
        Game g = game(a, run("p1", "H5", "H6", "H7"));
        expectError(() -> g.play("a", "p1", ids(a, "H8"), null), "R8");
    }

    // ------------------------------------------------------------- joker ambigu

    @Test void jokerTunggalDiUrutanMintaPilihan() {
        PlayerState a = player("a", true, cards("JK", "D2"));
        Game g = game(a, run("p1", "S4", "S5", "S6"));
        assertThatThrownBy(() -> g.play("a", "p1", ids(a, "JK"), null))
                .isInstanceOf(MoveException.class)
                .satisfies(e -> {
                    MoveException m = (MoveException) e;
                    assertThat(m.code()).isEqualTo("AMBIGUOUS");
                    assertThat(m.options()).hasSize(2);
                });
        g.play("a", "p1", ids(a, "JK"), 1);   // ke bawah: joker = 3♠
        assertThat(g.piles().get(0).low()).isEqualTo(3);
    }

    // ------------------------------------------------------------- selesai murni

    @Test void selesaiMurniLangsungSelesaiWalauLainMasihBanyakKartu() {
        PlayerState a = player("a", true, cards("H8"));
        Game g = game(a, run("p1", "H5", "H6", "H7"));
        g.play("a", "p1", ids(a, "H8"), null);
        assertThat(g.phase()).isEqualTo(Game.Phase.FINISHED);
        List<Game.Result> r = g.results();
        assertThat(r.get(0).playerId()).isEqualTo("a");
        assertThat(r.get(0).status()).isEqualTo(PlayerState.Status.FINISHED);
        assertThat(r.get(1).value()).isEqualTo(2 + 4 + 9 + 10);   // sisa kartu b tetap dihitung
    }

    // ------------------------------------------------------------- skor & peringkat

    @Test void nilaiSisaKartu() {
        PlayerState a = player("a", true, cards("SA", "H3", "DJ"));
        assertThat(a.handValue()).isEqualTo(28);
        assertThat(player("x", true, cards("C10", "JK", "H2")).handValue()).isEqualTo(37);
    }

    @Test void semuaMatiDanSeriYangTerakhirMatiMenang() {
        // a dan b sama-sama tidak bisa jalan; nilai sama 10
        PlayerState a = player("a", true, cards("H10"));
        PlayerState b = player("b", true, cards("S10"));
        Game g = Game.forTest(List.of(a, b));
        g.testSetTurn("a");
        assertThat(Rules.hasLegalMove(a.handRaw(), true, g.piles())).isFalse();
        a.die(1);
        b.die(2);
        List<Game.Result> r = g.results();
        assertThat(r.get(0).playerId()).isEqualTo("b");
    }

    // ------------------------------------------------------------- simulasi penuh

    @Test void simulasiPermainanSelaluSelesaiDanKartuUtuh() {
        for (int seed = 0; seed < 150; seed++) {
            int n = 4 + (seed % 2);
            String[] idName = new String[n * 2];
            for (int i = 0; i < n; i++) { idName[i * 2] = "u" + i; idName[i * 2 + 1] = "P" + i; }
            Map<String, String> names = Game.names(idName);
            Game g = Game.start(names, new Random(seed));
            int guard = 0;
            while (g.phase() == Game.Phase.PLAYING) {
                assertThat(guard++).isLessThan(500);
                PlayerState cur = g.currentPlayer();
                assertThat(Rules.hasLegalMove(cur.handRaw(), cur.hasDasar(), g.piles())).isTrue();
                assertThat(BotPlayer.playAny(g, cur)).as("seed %d: bot harus menemukan langkah", seed).isTrue();
                int onTable = g.piles().stream().mapToInt(p -> p.slots().size()).sum();
                int inHands = g.players().stream().mapToInt(p -> p.handRaw().size()).sum();
                assertThat(onTable + inHands + g.stockSize()).isEqualTo(108);
            }
            assertThat(g.results()).hasSize(n);
        }
    }
}
