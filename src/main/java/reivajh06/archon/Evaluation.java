package reivajh06.archon;

/** Handcrafted, tapered centipawn evaluation. Tables are generated once, without external assets. */
final class Evaluation {
    static final int[] VALUE = {100, 320, 335, 500, 950, 20000};
    private static final int[] MG_VALUE = {100, 320, 335, 500, 950, 0};
    private static final int[] EG_VALUE = {120, 305, 330, 520, 930, 0};
    private static final int[] PHASE = {0, 1, 1, 2, 4, 0};
    private static final int[][] MG = new int[6][64], EG = new int[6][64];
    private static final long[] FILE = new long[8];
    private static final long[][] PASSED = new long[2][64];
    static {
        for (int f = 0; f < 8; f++) FILE[f] = 0x0101010101010101L << f;
        for (int s = 0; s < 64; s++) {
            int f = s % 8, r = s / 8, center = 7 - Math.abs(2 * f - 7) - Math.abs(2 * r - 7);
            MG[Board.PAWN][s] = 7 * r + 3 * center;
            EG[Board.PAWN][s] = 12 * r + 2 * center;
            MG[Board.KNIGHT][s] = 9 * center - (r == 0 ? 12 : 0); EG[Board.KNIGHT][s] = 6 * center;
            MG[Board.BISHOP][s] = 5 * center + 3 * r; EG[Board.BISHOP][s] = 4 * center;
            MG[Board.ROOK][s] = 2 * center + (r == 6 ? 20 : 0); EG[Board.ROOK][s] = 3 * center;
            MG[Board.QUEEN][s] = 2 * center - (r > 2 ? 8 : 0); EG[Board.QUEEN][s] = 4 * center;
            MG[Board.KING][s] = -7 * center - 10 * r + (r == 0 && (f == 1 || f == 2 || f == 6) ? 25 : 0);
            EG[Board.KING][s] = 10 * center;
            for (int color = 0; color < 2; color++) for (int rank = r + (color == 0 ? 1 : -1);
                    rank >= 0 && rank < 8; rank += color == 0 ? 1 : -1)
                for (int file = Math.max(0, f - 1); file <= Math.min(7, f + 1); file++) PASSED[color][s] |= 1L << (rank * 8 + file);
        }
    }
    static int evaluate(Board b) {
        int mg = 0, eg = 0, phase = 0; long occ = b.all();
        for (int c = 0; c < 2; c++) {
            int sign = c == 0 ? 1 : -1, base = c * 6;
            long pawns = b.pieces[base], otherPawns = b.pieces[(c ^ 1) * 6];
            long enemyPawnAttacks = c == 0 ? ((otherPawns >>> 7) & ~FILE[0]) | ((otherPawns >>> 9) & ~FILE[7])
                    : ((otherPawns << 7) & ~FILE[7]) | ((otherPawns << 9) & ~FILE[0]);
            for (int type = 0; type < 6; type++) {
                long bits = b.pieces[base + type]; int count = Long.bitCount(bits); phase += count * PHASE[type];
                if (type == Board.BISHOP && count >= 2) { mg += sign * 28; eg += sign * 40; }
                while (bits != 0) {
                    int s = Long.numberOfTrailingZeros(bits); bits &= bits - 1;
                    int relative = c == 0 ? s : s ^ 56;
                    mg += sign * (MG_VALUE[type] + MG[type][relative]); eg += sign * (EG_VALUE[type] + EG[type][relative]);
                    int f = s % 8;
                    if (type == Board.PAWN) {
                        if (Long.bitCount(pawns & FILE[f]) > 1) { mg -= sign * 10; eg -= sign * 16; }
                        long adjacent = (f > 0 ? FILE[f - 1] : 0) | (f < 7 ? FILE[f + 1] : 0);
                        if ((adjacent & pawns) == 0) { mg -= sign * 13; eg -= sign * 10; }
                        if ((PASSED[c][s] & otherPawns) == 0) {
                            int advance = relative / 8; mg += sign * advance * advance * 2; eg += sign * advance * advance * 5;
                        }
                    } else if (type != Board.KING) {
                        int mobility = Long.bitCount(Attacks.piece(type, s, occ) & ~b.occupied[c] & ~enemyPawnAttacks);
                        int weight = type == Board.QUEEN ? 1 : type == Board.ROOK ? 2 : 4;
                        mg += sign * weight * mobility; eg += sign * (weight + 1) * mobility;
                        if (type == Board.ROOK && (pawns & FILE[f]) == 0) {
                            mg += sign * ((otherPawns & FILE[f]) == 0 ? 22 : 12); eg += sign * 10;
                        }
                    } else {
                        long ring = Attacks.KING[s];
                        mg += sign * 8 * Long.bitCount(ring & pawns);
                        int threats = Long.bitCount(ring & enemyPawnAttacks);
                        for (int enemyType = Board.KNIGHT; enemyType <= Board.QUEEN; enemyType++) {
                            long enemies = b.pieces[(c ^ 1) * 6 + enemyType];
                            while (enemies != 0) {
                                int e = Long.numberOfTrailingZeros(enemies); enemies &= enemies - 1;
                                threats += Long.bitCount(Attacks.piece(enemyType, e, occ) & ring);
                            }
                        }
                        mg -= sign * threats * 6;
                    }
                }
            }
        }
        phase = Math.min(24, phase);
        int value = (mg * phase + eg * (24 - phase)) / 24;
        return (b.side == 0 ? value : -value) + 10;
    }
}
