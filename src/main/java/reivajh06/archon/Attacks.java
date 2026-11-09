package reivajh06.archon;

final class Attacks {
    static final long[] KNIGHT = new long[64], KING = new long[64];
    static final long[][] PAWN = new long[2][64], RAY = new long[8][64];
    private static final int[] DF = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] DR = {1, 1, 0, -1, -1, -1, 0, 1};
    static {
        for (int s = 0; s < 64; s++) {
            int f = s % 8, r = s / 8;
            for (int d = 0; d < 8; d++) {
                for (int x = f + DF[d], y = r + DR[d]; inside(x, y); x += DF[d], y += DR[d])
                    RAY[d][s] |= 1L << (y * 8 + x);
                if (inside(f + DF[d], r + DR[d])) KING[s] |= 1L << ((r + DR[d]) * 8 + f + DF[d]);
            }
            for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++)
                if (Math.abs(x * y) == 2 && inside(f + x, r + y)) KNIGHT[s] |= 1L << ((r + y) * 8 + f + x);
            for (int c = 0; c < 2; c++) for (int x : new int[]{-1, 1}) {
                int y = r + (c == 0 ? 1 : -1);
                if (inside(f + x, y)) PAWN[c][s] |= 1L << (y * 8 + f + x);
            }
        }
    }
    private static boolean inside(int x, int y) { return x >= 0 && x < 8 && y >= 0 && y < 8; }
    static long slide(int s, long occupancy, int start) {
        long result = 0;
        for (int d = start; d < 8; d += 2) {
            long ray = RAY[d][s], blockers = ray & occupancy;
            if (blockers != 0) {
                int nearest = (d == 0 || d == 1 || d == 2 || d == 7)
                        ? Long.numberOfTrailingZeros(blockers) : 63 - Long.numberOfLeadingZeros(blockers);
                ray ^= RAY[d][nearest];
            }
            result |= ray;
        }
        return result;
    }
    static long bishop(int s, long occ) { return slide(s, occ, 1); }
    static long rook(int s, long occ) { return slide(s, occ, 0); }
    static long piece(int type, int s, long occ) {
        return switch (type) {
            case Board.KNIGHT -> KNIGHT[s];
            case Board.BISHOP -> bishop(s, occ);
            case Board.ROOK -> rook(s, occ);
            case Board.QUEEN -> bishop(s, occ) | rook(s, occ);
            case Board.KING -> KING[s];
            default -> 0;
        };
    }
}
