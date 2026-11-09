package reivajh06.archon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoardTest {
    static final String KIWIPETE = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
    static final String POSITION_3 = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1";
    private void perft(String fen, long... expected) {
        Board board = new Board(fen);
        for (int d = 1; d <= expected.length; d++) {
            assertEquals(expected[d - 1], board.perft(d), "depth " + d + ": " + fen);
            assertEquals(fen, board.fen()); assertEquals(board.recomputeHash(), board.hash());
        }
    }
    @Test void startPositionPerftThroughFive() { perft(Board.START_FEN, 20, 400, 8902, 197281, 4865609); }
    @Test void kiwipetePerftThroughFour() { perft(KIWIPETE, 48, 2039, 97862, 4085603); }
    @Test void positionThreePerftThroughFive() { perft(POSITION_3, 14, 191, 2812, 43238, 674624); }
    @Test void additionalPromotionAndCheckBenchmarks() {
        perft("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", 6, 264, 9467, 422333);
        perft("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 44, 1486, 62379);
        perft("r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10", 46, 2079, 89890);
    }
    @Test void fenRoundTripAndUndo() {
        for (String fen : new String[]{Board.START_FEN, KIWIPETE, POSITION_3,
                "r3k2r/8/8/3pP3/8/8/8/R3K2R w KQkq d6 17 42", "4k3/P7/8/8/8/8/7p/4K3 b - - 99 53"}) {
            Board board = new Board(fen); long hash = board.hash(); assertEquals(fen, board.fen());
            for (int move : board.legalMoves()) {
                board.make(move); assertEquals(board.recomputeHash(), board.hash(), Move.uci(move));
                assertEquals(board.hash(), new Board(board.fen()).hash());
                board.unmake(); assertEquals(fen, board.fen()); assertEquals(hash, board.hash());
            }
        }
    }
    @Test void pinnedEnPassantIsIllegalAndDoesNotAffectHash() {
        Board b = new Board("4k3/8/8/r4pPK/8/8/8/8 w - f6 0 1");
        assertFalse(java.util.Arrays.stream(b.legalMoves()).anyMatch(m -> Move.uci(m).equals("g5f6")));
        assertEquals(new Board("4k3/8/8/r4pPK/8/8/8/8 w - - 0 1").hash(), b.hash());
    }
    @Test void threefoldAndFiftyMoveState() {
        Board board = new Board(Board.START_FEN);
        for (int cycle = 0; cycle < 2; cycle++) {
            for (String uci : new String[]{"g1f3", "g8f6", "f3g1", "f6g8"}) {
                int move = java.util.Arrays.stream(board.legalMoves()).filter(m -> Move.uci(m).equals(uci)).findFirst().orElseThrow();
                board.make(move);
            }
            assertEquals(cycle == 1, board.repetition());
        }
        assertEquals(8, board.halfmove);
    }
    @Test void rejectsMalformedFen() {
        assertThrows(IllegalArgumentException.class, () -> new Board("8/8/8/8/8/8/8/8 w - - 0 1"));
        assertThrows(IllegalArgumentException.class, () -> new Board(Board.START_FEN.replace(" w ", " x ")));
        assertThrows(IllegalArgumentException.class, () -> new Board("8/2p5/3p4/KP5r/1R3p2/8/4P1P1/8 w - - 0 1"));
    }
}
