package reivajh06.archon;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import reivajh06.plugins.PluginConfiguration.Difficulty;
import static org.junit.jupiter.api.Assertions.*;

class SearchTest {
    @Test void completedPvIsLegalAndSearchRestoresBoard() {
        for (String fen : new String[]{Board.START_FEN, BoardTest.KIWIPETE, BoardTest.POSITION_3}) {
            Board b = new Board(fen); long key = b.hash();
            Search.Result r = new Search().search(b, 50, Difficulty.MAX);
            assertEquals(fen, b.fen()); assertEquals(key, b.hash()); assertEquals(0, b.ply);
            assertEquals(Move.uci(r.move()), r.pv().split(" ")[0]);
            for (String uci : r.pv().split(" ")) {
                int move = Arrays.stream(b.legalMoves()).filter(m -> Move.uci(m).equals(uci)).findFirst().orElseThrow(); b.make(move);
            }
        }
    }
    @Test void drawsAndMateTakePrecedenceCorrectly() {
        Search s = new Search();
        assertEquals(0, s.search(new Board("4k3/8/8/8/8/8/8/R3K3 w - - 100 71"), 10, Difficulty.EASY).score());
        assertEquals(-Search.MATE, s.search(new Board("7k/6Q1/5K2/8/8/8/8/8 b - - 100 71"), 10, Difficulty.EASY).score());
        assertEquals(0, s.search(new Board("4k3/8/8/8/8/8/8/4K3 w - - 0 1"), 10, Difficulty.EASY).score());
        Board b = new Board(Board.START_FEN);
        for (int i = 0; i < 2; i++) for (String uci : new String[]{"g1f3", "g8f6", "f3g1", "f6g8"})
            b.make(Arrays.stream(b.legalMoves()).filter(m -> Move.uci(m).equals(uci)).findFirst().orElseThrow());
        assertEquals(0, s.search(b, 10, Difficulty.EASY).score());
    }
    @Test void staticExchangeRecognizesPoisonedCapture() {
        Board b = new Board("3rk3/8/8/3p4/8/8/8/3QK3 w - - 0 1");
        int m = Arrays.stream(b.legalMoves()).filter(x -> Move.uci(x).equals("d1d5")).findFirst().orElseThrow();
        assertTrue(StaticExchange.evaluate(b, m) < 0);
    }
    @Test void nullMoveUndoAndHash() {
        Board b = new Board("r3k2r/8/8/3pP3/8/8/8/R3K2R w KQkq d6 0 42");
        String fen = b.fen(); long hash = b.hash(); b.makeNull();
        assertEquals(b.recomputeHash(), b.hash()); assertEquals(-1, b.ep);
        b.unmake(); assertEquals(fen, b.fen()); assertEquals(hash, b.hash());
    }
    @Test void transpositionMateNormalizationAndClockIsolation() {
        TranspositionTable tt = new TranspositionTable(); tt.newSearch();
        tt.store(123, 0, 5, Search.MATE - 7, TranspositionTable.EXACT, 12, 3);
        int i = tt.probe(123, 0); assertTrue(i >= 0); assertEquals(Search.MATE - 9, tt.score(i, 5));
        assertEquals(-1, tt.probe(123, 99));
    }
}
