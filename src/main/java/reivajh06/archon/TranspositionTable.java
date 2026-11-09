package reivajh06.archon;

/** Fixed-size direct-mapped table, favoring deeper entries from the current search. */
final class TranspositionTable {
    static final int EXACT = 1, LOWER = 2, UPPER = 3;
    private static final int SIZE = 1 << 18;
    private final long[] keys = new long[SIZE];
    private final int[] moves = new int[SIZE], scores = new int[SIZE], ages = new int[SIZE];
    private final short[] depths = new short[SIZE], clocks = new short[SIZE];
    private final byte[] flags = new byte[SIZE];
    private int age;
    void newSearch() { age++; }
    private int index(long key) { return (int) (key ^ (key >>> 32)) & (SIZE - 1); }
    int probe(long key, int halfmove) {
        int i = index(key); return flags[i] != 0 && keys[i] == key && clocks[i] == Math.min(100, halfmove) ? i : -1;
    }
    int move(int i) { return moves[i]; }
    int depth(int i) { return depths[i]; }
    int flag(int i) { return flags[i]; }
    int score(int i, int ply) {
        int score = scores[i]; return score > Search.MATE_THRESHOLD ? score - ply : score < -Search.MATE_THRESHOLD ? score + ply : score;
    }
    void store(long key, int halfmove, int depth, int score, int flag, int move, int ply) {
        int i = index(key);
        if (flags[i] != 0 && ages[i] == age && depths[i] > depth + (keys[i] == key ? 0 : 2)) return;
        keys[i] = key; depths[i] = (short) depth; clocks[i] = (short) Math.min(100, halfmove);
        scores[i] = score > Search.MATE_THRESHOLD ? score + ply : score < -Search.MATE_THRESHOLD ? score - ply : score;
        flags[i] = (byte) flag; moves[i] = move; ages[i] = age;
    }
}
