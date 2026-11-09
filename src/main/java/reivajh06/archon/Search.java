package reivajh06.archon;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import reivajh06.plugins.PluginConfiguration.Difficulty;

/** Single-threaded iterative PVS. Only completed iterations are published to the caller. */
public final class Search {
    static final int MATE = 30000, MATE_THRESHOLD = 29000, INF = 31000, MAX_PLY = 96;
    private static final Stop STOP = new Stop();
    private static final class Stop extends RuntimeException {
        Stop() { super(null, null, false, false); }
    }
    public record Result(int move, int score, int depth, long nodes, long timeMs, String pv) {
        /** Public performance statistics only: evaluation and expected replies stay inside the engine. */
        public Map<String, String> details() {
            Map<String, String> d = new LinkedHashMap<>();
            d.put("depth", Integer.toString(depth)); d.put("nodes", Long.toString(nodes));
            d.put("nps", Long.toString(nodes * 1000 / Math.max(1, timeMs)));
            d.put("time", Long.toString(timeMs));
            return d;
        }
    }
    private final TranspositionTable tt = new TranspositionTable();
    private final MoveList[] lists = new MoveList[MAX_PLY];
    private final int[][] killers = new int[MAX_PLY][2], history = new int[12][64];
    private final int[][] pv = new int[MAX_PLY][MAX_PLY];
    private final int[] pvLength = new int[MAX_PLY];
    private long nodes, deadline;
    private boolean timed;
    private int iterationMove;
    private int[] rootMoves, rootScores;
    public Search() { for (int i = 0; i < MAX_PLY; i++) lists[i] = new MoveList(); }

    public Result search(Board b, long moveTimeMs, Difficulty difficulty) {
        long start = System.nanoTime();
        long budget = Math.min(moveTimeMs > 0 ? moveTimeMs : 1000, 86_400_000L) * 1_000_000;
        deadline = start + budget; nodes = 0; timed = false; tt.newSearch();
        for (int[] row : history) for (int i = 0; i < row.length; i++) row[i] /= 2;
        for (int[] row : killers) Arrays.fill(row, 0);
        b.legalMoves(lists[0]);
        if (lists[0].size == 0) return new Result(0, b.inCheck() ? -MATE : 0, 0, 0, 0, "");
        rootMoves = Arrays.copyOf(lists[0].moves, lists[0].size);
        rootScores = new int[rootMoves.length]; Arrays.fill(rootScores, -INF);
        int best = rootMoves[0], score = 0, completed = 0;
        String line = Move.uci(best);
        int cap = switch (difficulty) { case EASY -> 2; case MEDIUM -> 5; case HARD -> 9; case MAX -> MAX_PLY - 8; };
        int[] completedScores = rootScores.clone();
        for (int depth = 1; depth <= cap; depth++) {
            timed = depth > 1;
            int window = depth >= 4 ? 30 : INF, alpha = Math.max(-INF, score - window), beta = Math.min(INF, score + window);
            try {
                int value;
                while (true) {
                    value = root(b, depth, alpha, beta, best, difficulty == Difficulty.EASY || difficulty == Difficulty.MEDIUM);
                    if (value <= alpha && alpha > -INF) { window *= 2; alpha = Math.max(-INF, value - window); }
                    else if (value >= beta && beta < INF) { window *= 2; beta = Math.min(INF, value + window); }
                    else break;
                }
                best = iterationMove; score = value; completed = depth;
                completedScores = rootScores.clone(); line = pvString(0);
            } catch (Stop stop) { break; }
            if (Math.abs(score) >= MATE - MAX_PLY || System.nanoTime() - start >= budget) break;
        }
        // Weaken by selecting among fully searched root scores, never by making an illegal move.
        int noise = difficulty == Difficulty.EASY ? 180 : difficulty == Difficulty.MEDIUM ? 25 : 0;
        if (noise > 0 && Math.abs(score) < MATE_THRESHOLD) {
            int chosen = best, chosenScore = score, noisyBest = -INF;
            for (int i = 0; i < rootMoves.length; i++) {
                int s = completedScores[i];
                if (s < score - noise || Math.abs(s) > MATE_THRESHOLD) continue;
                int noisy = s + ThreadLocalRandom.current().nextInt(noise + 1);
                if (noisy > noisyBest) { noisyBest = noisy; chosen = rootMoves[i]; chosenScore = s; }
            }
            if (chosen != best) line = Move.uci(chosen);
            best = chosen; score = chosenScore;
        }
        return new Result(best, score, completed, nodes, (System.nanoTime() - start) / 1_000_000, line);
    }
    private int root(Board b, int depth, int alpha, int beta, int previous, boolean allScores) {
        checkTime(); pvLength[0] = 0; iterationMove = previous;
        MoveList list = lists[0]; b.legalMoves(list); order(b, list, previous, 0);
        int bestScore = -INF; Arrays.fill(rootScores, -INF);
        boolean drawn = b.halfmove >= 100 || b.repetition() || b.insufficientMaterial();
        for (int i = 0; i < list.size; i++) {
            checkTime(); int move = list.pick(i), value;
            b.make(move);
            try {
                if (drawn) { value = 0; pvLength[1] = 1; }
                else if (allScores) value = -negamax(b, depth - 1, -INF, INF, 1, true);
                else if (i == 0) value = -negamax(b, depth - 1, -beta, -alpha, 1, true);
                else {
                    value = -negamax(b, depth - 1, -alpha - 1, -alpha, 1, true);
                    if (value > alpha && value < beta) value = -negamax(b, depth - 1, -beta, -alpha, 1, true);
                }
            } finally { b.unmake(); }
            for (int j = 0; j < rootMoves.length; j++) if (rootMoves[j] == move) rootScores[j] = value;
            if (value > bestScore) { bestScore = value; iterationMove = move; updatePv(0, move); }
            alpha = Math.max(alpha, value);
            if (alpha >= beta) break;
        }
        return bestScore;
    }
    private int negamax(Board b, int depth, int alpha, int beta, int ply, boolean allowNull) {
        visit(); pvLength[ply] = ply;
        boolean check = b.inCheck();
        if (ply >= MAX_PLY - 1) return Evaluation.evaluate(b);
        MoveList list = lists[ply]; b.legalMoves(list);
        if (list.size == 0) return check ? -MATE + ply : 0;
        if (b.halfmove >= 100 || b.repetition() || b.insufficientMaterial()) return 0;
        alpha = Math.max(alpha, -MATE + ply); beta = Math.min(beta, MATE - ply - 1);
        if (alpha >= beta) return alpha;
        if (check) depth++;
        if (depth <= 0) return quiescence(b, alpha, beta, ply, 0);
        int oldAlpha = alpha, ttMove = 0, slot = tt.probe(b.hash, b.halfmove);
        boolean pvNode = beta - alpha > 1;
        if (slot >= 0) {
            ttMove = tt.move(slot); int value = tt.score(slot, ply);
            if (!pvNode && tt.depth(slot) >= depth && (tt.flag(slot) == TranspositionTable.EXACT
                    || (tt.flag(slot) == TranspositionTable.LOWER && value >= beta)
                    || (tt.flag(slot) == TranspositionTable.UPPER && value <= alpha))) return value;
        }
        if (allowNull && !pvNode && !check && depth >= 3 && b.hasNonPawnMaterial() && b.halfmove < 90
                && Evaluation.evaluate(b) >= beta && Math.abs(beta) < MATE_THRESHOLD) {
            int value; b.makeNull();
            try { value = -negamax(b, depth - 1 - (2 + depth / 5), -beta, -beta + 1, ply + 1, false); }
            finally { b.unmake(); }
            if (value >= beta) return Math.min(value, MATE_THRESHOLD);
        }
        order(b, list, ttMove, ply);
        int bestScore = -INF, bestMove = 0;
        for (int i = 0; i < list.size; i++) {
            int move = list.pick(i), piece = b.squares[Move.from(move)];
            boolean quiet = !Move.capture(move) && Move.promotion(move) == 0;
            b.make(move); int value;
            try {
                int reduction = depth >= 3 && i >= 4 && quiet && !check && !b.inCheck()
                        ? Math.min(depth - 2, 1 + (i >= 10 ? 1 : 0) + (depth >= 6 ? 1 : 0)) : 0;
                if (i == 0) value = -negamax(b, depth - 1, -beta, -alpha, ply + 1, true);
                else {
                    value = -negamax(b, depth - 1 - reduction, -alpha - 1, -alpha, ply + 1, true);
                    if (reduction > 0 && value > alpha) value = -negamax(b, depth - 1, -alpha - 1, -alpha, ply + 1, true);
                    if (value > alpha && value < beta) value = -negamax(b, depth - 1, -beta, -alpha, ply + 1, true);
                }
            } finally { b.unmake(); }
            if (value > bestScore) { bestScore = value; bestMove = move; }
            if (value > alpha) { alpha = value; updatePv(ply, move); }
            if (alpha >= beta) {
                if (quiet) {
                    if (killers[ply][0] != move) { killers[ply][1] = killers[ply][0]; killers[ply][0] = move; }
                    int bonus = Math.min(1500, depth * depth * 24), h = history[piece][Move.to(move)];
                    history[piece][Move.to(move)] = h + bonus - h * bonus / 16384;
                }
                break;
            }
        }
        int flag = bestScore >= beta ? TranspositionTable.LOWER : bestScore <= oldAlpha ? TranspositionTable.UPPER : TranspositionTable.EXACT;
        tt.store(b.hash, b.halfmove, depth, bestScore, flag, bestMove, ply);
        return bestScore;
    }
    private int quiescence(Board b, int alpha, int beta, int ply, int qDepth) {
        visit(); pvLength[ply] = ply;
        boolean check = b.inCheck();
        if (ply >= MAX_PLY - 1) return Evaluation.evaluate(b);
        MoveList list = lists[ply]; b.legalMoves(list);
        if (list.size == 0) return check ? -MATE + ply : 0;
        if (b.halfmove >= 100 || b.repetition() || b.insufficientMaterial()) return 0;
        int stand = Evaluation.evaluate(b);
        if (!check) {
            if (stand >= beta) return stand;
            alpha = Math.max(alpha, stand);
            if (qDepth >= 16) return alpha;
        }
        order(b, list, 0, ply);
        for (int i = 0; i < list.size; i++) {
            int move = list.pick(i);
            if (!check && !Move.capture(move) && Move.promotion(move) == 0) continue;
            boolean losing = !check && Move.promotion(move) == 0 && StaticExchange.evaluate(b, move) < 0;
            b.make(move); int value;
            try {
                if (losing && !b.inCheck()) continue;
                value = -quiescence(b, -beta, -alpha, ply + 1, qDepth + 1);
            } finally { b.unmake(); }
            if (value >= beta) return value;
            if (value > alpha) { alpha = value; updatePv(ply, move); }
        }
        return alpha;
    }
    private void order(Board b, MoveList list, int ttMove, int ply) {
        for (int i = 0; i < list.size; i++) {
            int m = list.moves[i], score;
            if (m == ttMove) score = 2_000_000;
            else if (Move.capture(m) || Move.promotion(m) != 0) {
                int victim = (m & Move.EN_PASSANT) != 0 ? Board.PAWN : b.squares[Move.to(m)] < 0 ? Board.PAWN : b.squares[Move.to(m)] % 6;
                score = 1_000_000 + Evaluation.VALUE[victim] * 16 - Evaluation.VALUE[b.squares[Move.from(m)] % 6]
                        + (Move.promotion(m) == 0 ? 0 : Evaluation.VALUE[Move.promotion(m)] * 16);
                if (StaticExchange.evaluate(b, m) < 0) score -= 1_100_000;
            } else if (m == killers[ply][0]) score = 900_000;
            else if (m == killers[ply][1]) score = 800_000;
            else score = history[b.squares[Move.from(m)]][Move.to(m)];
            list.scores[i] = score;
        }
    }
    private void updatePv(int ply, int move) {
        pv[ply][ply] = move;
        int length = Math.max(ply + 1, pvLength[ply + 1]);
        for (int i = ply + 1; i < length; i++) pv[ply][i] = pv[ply + 1][i];
        pvLength[ply] = length;
    }
    private String pvString(int ply) {
        StringBuilder b = new StringBuilder();
        for (int i = ply; i < pvLength[ply]; i++) { if (!b.isEmpty()) b.append(' '); b.append(Move.uci(pv[ply][i])); }
        return b.toString();
    }
    private void visit() { nodes++; if ((nodes & 255) == 0) checkTime(); }
    private void checkTime() { if (timed && (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline)) throw STOP; }
}
