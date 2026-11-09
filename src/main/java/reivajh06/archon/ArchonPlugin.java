package reivajh06.archon;

import java.util.LinkedHashMap;
import java.util.Map;
import reivajh06.plugins.Plugin;
import reivajh06.plugins.PluginConfiguration;
import reivajh06.plugins.PluginResponse;

/** ServiceLoader entry point. No processes, native libraries, files, or background threads are needed. */
public final class ArchonPlugin implements Plugin {
    private final Search search = new Search();
    private PluginConfiguration.Difficulty difficulty = PluginConfiguration.Difficulty.MAX;
    private long moveTimeMs = 1000;
    private boolean ready;
    private Board tracked;

    @Override public synchronized String name() { return "Archon"; }
    @Override public synchronized PluginType type() { return PluginType.CPU; }
    /** Archchess reloads plugins every minute; starting again preserves configuration and game history. */
    @Override public synchronized void start() { ready = true; }
    @Override public synchronized boolean isReady() { return ready; }
    public synchronized void stop() { ready = false; tracked = null; }
    @Override public synchronized boolean sendParameters(PluginConfiguration configuration) {
        if (configuration == null || configuration.difficulty() == null) return false;
        difficulty = configuration.difficulty();
        if (configuration.moveTimeMs() > 0) moveTimeMs = configuration.moveTimeMs();
        return true;
    }
    @Override public synchronized PluginResponse requestMove(String fen) {
        if (!ready) start();
        try {
            Board incoming = new Board(fen);
            Board board = followGame(incoming);
            Search.Result result = search.search(board, moveTimeMs, difficulty);
            if (result.move() == 0) return new PluginResponse(Map.of("error", "no legal moves"), result.details());
            Map<String, String> message = new LinkedHashMap<>();
            message.put("from", Move.square(Move.from(result.move())));
            message.put("to", Move.square(Move.to(result.move())));
            if (Move.promotion(result.move()) != 0) message.put("promote_to", switch (Move.promotion(result.move())) {
                case Board.QUEEN -> "queen"; case Board.ROOK -> "rook"; case Board.BISHOP -> "bishop"; case Board.KNIGHT -> "knight";
                default -> throw new IllegalArgumentException("Invalid promotion");
            });
            board.make(result.move()); tracked = board;
            return new PluginResponse(message, result.details());
        } catch (RuntimeException e) {
            tracked = null;
            return new PluginResponse(Map.of("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), Map.of());
        }
    }
    /** CPU engines generate their own legal moves; the second argument belongs to the host's AI flow. */
    @Override public synchronized PluginResponse requestMove(String fen, String legalMoves) { return requestMove(fen); }

    // Retain repetitions across sequential requests when the opponent's intervening move can be inferred.
    // A fresh FEN alone cannot describe moves that preceded the first request, or another game using this service.
    private Board followGame(Board incoming) {
        if (tracked == null || incoming.halfmove == 0 || tracked.ply > 800) return incoming;
        String fen = incoming.fen();
        if (tracked.fen().equals(fen)) return tracked;
        for (int move : tracked.legalMoves()) {
            tracked.make(move);
            if (tracked.fen().equals(fen)) return tracked;
            tracked.unmake();
        }
        return incoming;
    }
}
