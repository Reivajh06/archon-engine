package reivajh06.archon;

/** Swap-off SEE with occupancy updates, x-ray attackers, and least valuable recaptures. */
final class StaticExchange {
    private StaticExchange() {}
    static int evaluate(Board b, int move) {
        int from = Move.from(move), to = Move.to(move), piece = b.squares[from] % 6;
        int captured = (move & Move.EN_PASSANT) != 0 ? Board.PAWN : b.squares[to] < 0 ? -1 : b.squares[to] % 6;
        int[] gain = new int[32];
        gain[0] = captured < 0 ? 0 : Evaluation.VALUE[captured];
        if (Move.promotion(move) != 0) { gain[0] += Evaluation.VALUE[Move.promotion(move)] - Evaluation.VALUE[Board.PAWN]; piece = Move.promotion(move); }
        long occ = b.all() & ~(1L << from);
        if ((move & Move.EN_PASSANT) != 0) occ &= ~(1L << (to + (b.side == 0 ? -8 : 8)));
        occ |= 1L << to;
        int side = b.side ^ 1, depth = 0;
        while (depth < 30) {
            long attackers = attackers(b, to, side, occ);
            int attacker = -1, square = -1;
            for (int type = 0; type < 6; type++) {
                long candidates = attackers & b.pieces[side * 6 + type] & occ;
                if (candidates != 0) { attacker = type; square = Long.numberOfTrailingZeros(candidates); break; }
            }
            if (attacker < 0) break;
            // A king may not recapture onto a defended square.
            if (attacker == Board.KING && attackers(b, to, side ^ 1, occ & ~(1L << square)) != 0) break;
            gain[++depth] = Evaluation.VALUE[piece] - gain[depth - 1];
            piece = attacker; occ &= ~(1L << square); side ^= 1;
        }
        while (depth > 0) { gain[depth - 1] = -Math.max(-gain[depth - 1], gain[depth]); depth--; }
        return gain[0];
    }
    private static long attackers(Board b, int s, int side, long occ) {
        int base = side * 6;
        // The captured occupant on the target square is never an attacker of its own square.
        return ((Attacks.PAWN[side ^ 1][s] & b.pieces[base])
                | (Attacks.KNIGHT[s] & b.pieces[base + Board.KNIGHT])
                | (Attacks.KING[s] & b.pieces[base + Board.KING])
                | (Attacks.bishop(s, occ) & (b.pieces[base + Board.BISHOP] | b.pieces[base + Board.QUEEN]))
                | (Attacks.rook(s, occ) & (b.pieces[base + Board.ROOK] | b.pieces[base + Board.QUEEN]))) & occ;
    }
}
