package reivajh06.archon;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import reivajh06.plugins.Plugin;
import reivajh06.plugins.PluginConfiguration;
import reivajh06.plugins.PluginConfiguration.Difficulty;
import reivajh06.plugins.PluginResponse;
import static org.junit.jupiter.api.Assertions.*;

class PluginTest {
    private ArchonPlugin plugin(long ms, Difficulty difficulty) {
        ArchonPlugin p = new ArchonPlugin();
        assertTrue(p.sendParameters(new PluginConfiguration(difficulty, ms))); p.start(); return p;
    }
    @Test void serviceLoaderAndRepeatedStart() {
        Plugin p = ServiceLoader.load(Plugin.class).stream().map(ServiceLoader.Provider::get)
                .filter(x -> x.name().equals("Archon")).findFirst().orElseThrow();
        assertEquals(Plugin.PluginType.CPU, p.type()); p.start(); p.start(); assertTrue(p.isReady());
        p.sendParameters(new PluginConfiguration(Difficulty.EASY, 1000)); p.start();
        assertTrue(Integer.parseInt(p.requestMove(Board.START_FEN).details().get("depth")) <= 2);
    }
    @Test void mateInOne() {
        PluginResponse r = plugin(1000, Difficulty.MAX).requestMove("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1");
        assertEquals("a1", r.message().get("from")); assertEquals("a8", r.message().get("to"));
        assertEquals(Set.of("depth", "nodes", "nps", "time"), r.details().keySet());
    }
    @Test void reportsPromotionForBothColors() {
        for (String fen : new String[]{"7k/P7/8/8/8/8/8/4K3 w - - 0 1", "4k3/8/8/8/8/8/p7/7K b - - 0 1"}) {
            PluginResponse r = plugin(50, Difficulty.MAX).requestMove(fen);
            assertEquals("queen", r.message().get("promote_to")); assertEquals("a", r.message().get("to").substring(0, 1));
            assertLegal(fen, r);
            assertEquals(4, Arrays.stream(new Board(fen).legalMoves()).filter(m -> Move.promotion(m) != 0).count());
        }
    }
    @Test void noLegalMovesReturnsError() {
        ArchonPlugin p = plugin(50, Difficulty.MAX);
        for (String fen : new String[]{"7k/6Q1/5K2/8/8/8/8/8 b - - 0 1", "7k/5K2/6Q1/8/8/8/8/8 b - - 0 1"}) {
            PluginResponse r = p.requestMove(fen);
            assertEquals("no legal moves", r.message().get("error"));
            assertEquals(Set.of("depth", "nodes", "nps", "time"), r.details().keySet());
        }
        assertTrue(p.requestMove("invalid").message().containsKey("error"));
    }
    @Test void fiftyMillisecondsReturnsLegalMoveAndStatistics() {
        ArchonPlugin p = plugin(50, Difficulty.MAX);
        assertTimeout(Duration.ofSeconds(2), () -> {
            long start = System.nanoTime(); PluginResponse r = p.requestMove(Board.START_FEN, "ignored");
            assertTrue((System.nanoTime() - start) / 1_000_000 < 1000);
            assertLegal(Board.START_FEN, r);
            assertTrue(Integer.parseInt(r.details().get("depth")) >= 1);
            assertEquals(Set.of("depth", "nodes", "nps", "time"), r.details().keySet());
            for (String field : r.details().keySet()) assertTrue(Long.parseLong(r.details().get(field)) >= 0);
        });
    }
    @Test void oneMillisecondStillCompletesDepthOne() {
        PluginResponse r = plugin(1, Difficulty.MAX).requestMove(Board.START_FEN);
        assertLegal(Board.START_FEN, r); assertTrue(Integer.parseInt(r.details().get("depth")) >= 1);
    }
    @Test void difficultyCapsAndConfigurationSurviveReload() {
        ArchonPlugin p = plugin(1000, Difficulty.EASY); p.start();
        assertTrue(Integer.parseInt(p.requestMove(Board.START_FEN).details().get("depth")) <= 2);
        assertTrue(p.sendParameters(new PluginConfiguration(Difficulty.MEDIUM, 50)));
        assertTrue(Integer.parseInt(p.requestMove(Board.START_FEN).details().get("depth")) <= 5);
        assertFalse(p.sendParameters(null)); assertFalse(p.sendParameters(new PluginConfiguration(null, 50)));
    }
    static void assertLegal(String fen, PluginResponse r) {
        assertFalse(r.message().containsKey("error"), r.message().toString());
        String uci = r.message().get("from") + r.message().get("to");
        if (r.message().containsKey("promote_to")) uci += switch (r.message().get("promote_to")) {
            case "queen" -> "q"; case "rook" -> "r"; case "bishop" -> "b"; case "knight" -> "n"; default -> "?";
        };
        String expected = uci;
        assertTrue(Arrays.stream(new Board(fen).legalMoves()).anyMatch(m -> Move.uci(m).equals(expected)), expected);
    }
}
