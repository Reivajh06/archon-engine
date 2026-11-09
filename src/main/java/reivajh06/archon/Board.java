package reivajh06.archon;

import java.util.Arrays;
import java.util.SplittableRandom;

/** Mutable bitboard position with incremental hashing and an allocation-free undo stack. */
public final class Board {
    public static final int PAWN = 0, KNIGHT = 1, BISHOP = 2, ROOK = 3, QUEEN = 4, KING = 5;
    public static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String SYMBOLS = "PNBRQKpnbrqk";
    private static final long[][] Z = new long[12][64];
    private static final long[] RIGHTS = new long[16], EP = new long[8];
    private static final long TURN;
    private static final int[] CASTLE_MASK = new int[64];
    static {
        SplittableRandom random = new SplittableRandom(0x415243484f4eL);
        for (long[] row : Z) for (int s = 0; s < 64; s++) row[s] = random.nextLong();
        for (int i = 0; i < 16; i++) RIGHTS[i] = random.nextLong();
        for (int i = 0; i < 8; i++) EP[i] = random.nextLong();
        TURN = random.nextLong();
        Arrays.fill(CASTLE_MASK, 15);
        CASTLE_MASK[0] = 13; CASTLE_MASK[4] = 12; CASTLE_MASK[7] = 14;
        CASTLE_MASK[56] = 7; CASTLE_MASK[60] = 3; CASTLE_MASK[63] = 11;
    }
    final long[] pieces = new long[12], occupied = new long[2];
    final int[] squares = new int[64];
    int side, castling, ep = -1, halfmove, fullmove, ply;
    long hash;
    private final int[] undoMove = new int[1024], undoCaptured = new int[1024], undoRights = new int[1024],
            undoEp = new int[1024], undoHalf = new int[1024];
    private final long[] undoHash = new long[1024];

    public Board(String fen) {
        if (fen == null) throw new IllegalArgumentException("FEN is required");
        String[] fields = fen.strip().split("\\s+");
        if (fields.length != 6) throw new IllegalArgumentException("FEN needs six fields");
        Arrays.fill(squares, -1);
        String[] ranks = fields[0].split("/", -1);
        if (ranks.length != 8) throw new IllegalArgumentException("FEN needs eight ranks");
        for (int r = 0; r < 8; r++) {
            int f = 0;
            for (char ch : ranks[r].toCharArray()) {
                if (ch >= '1' && ch <= '8') f += ch - '0';
                else {
                    int p = SYMBOLS.indexOf(ch);
                    if (p < 0 || f >= 8) throw new IllegalArgumentException("Invalid FEN piece placement");
                    put(p, (7 - r) * 8 + f++);
                }
                if (f > 8) throw new IllegalArgumentException("Invalid FEN rank");
            }
            if (f != 8) throw new IllegalArgumentException("Invalid FEN rank width");
        }
        if (!fields[1].equals("w") && !fields[1].equals("b")) throw new IllegalArgumentException("Invalid side");
        side = fields[1].equals("w") ? 0 : 1;
        if (!fields[2].equals("-")) for (char ch : fields[2].toCharArray()) {
            int bit = switch (ch) { case 'K' -> 1; case 'Q' -> 2; case 'k' -> 4; case 'q' -> 8;
                default -> throw new IllegalArgumentException("Invalid castling rights"); };
            if ((castling & bit) != 0) throw new IllegalArgumentException("Duplicate castling right");
            castling |= bit;
        }
        ep = fields[3].equals("-") ? -1 : Move.square(fields[3]);
        halfmove = Integer.parseInt(fields[4]); fullmove = Integer.parseInt(fields[5]);
        if (halfmove < 0 || fullmove < 1 || (ep >= 0 && ep / 8 != (side == 0 ? 5 : 2)))
            throw new IllegalArgumentException("Invalid FEN counters or en passant square");
        if (Long.bitCount(pieces[KING]) != 1 || Long.bitCount(pieces[6 + KING]) != 1)
            throw new IllegalArgumentException("FEN needs one king per side");
        if (((pieces[PAWN] | pieces[6 + PAWN]) & 0xff000000000000ffL) != 0)
            throw new IllegalArgumentException("Pawn on promotion rank");
        hash = recomputeHash();
    }
    public String fen() {
        StringBuilder b = new StringBuilder();
        for (int r = 7; r >= 0; r--) {
            int empty = 0;
            for (int f = 0; f < 8; f++) {
                int p = squares[r * 8 + f];
                if (p < 0) empty++;
                else { if (empty > 0) b.append(empty); empty = 0; b.append(SYMBOLS.charAt(p)); }
            }
            if (empty > 0) b.append(empty);
            if (r > 0) b.append('/');
        }
        b.append(side == 0 ? " w " : " b ");
        if (castling == 0) b.append('-');
        else for (int i = 0; i < 4; i++) if ((castling & (1 << i)) != 0) b.append("KQkq".charAt(i));
        return b.append(' ').append(ep < 0 ? "-" : Move.square(ep)).append(' ').append(halfmove).append(' ').append(fullmove).toString();
    }
    long all() { return occupied[0] | occupied[1]; }
    private void put(int p, int s) {
        long bit = 1L << s;
        pieces[p] |= bit; occupied[p / 6] |= bit; squares[s] = p; hash ^= Z[p][s];
    }
    private void remove(int p, int s) {
        long bit = 1L << s;
        pieces[p] ^= bit; occupied[p / 6] ^= bit; squares[s] = -1; hash ^= Z[p][s];
    }
    public long hash() { return hash; }
    long recomputeHash() {
        long h = RIGHTS[castling] ^ (side == 1 ? TURN : 0);
        int file = epFile(); if (file >= 0) h ^= EP[file];
        for (int s = 0; s < 64; s++) if (squares[s] >= 0) h ^= Z[squares[s]][s];
        return h;
    }
    // Only legally capturable en passant rights affect repetition identity (including pinned pawns).
    private int epFile() {
        if (ep < 0 || squares[ep] >= 0) return -1;
        int captured = ep + (side == 0 ? -8 : 8), enemy = side ^ 1;
        if (squares[captured] != enemy * 6 + PAWN) return -1;
        long pawns = Attacks.PAWN[enemy][ep] & pieces[side * 6];
        while (pawns != 0) {
            int from = Long.numberOfTrailingZeros(pawns); pawns &= pawns - 1;
            long occ = (all() ^ (1L << from) ^ (1L << captured)) | (1L << ep);
            if (!attacked(Long.numberOfTrailingZeros(pieces[side * 6 + KING]), enemy, occ, 1L << captured)) return ep % 8;
        }
        return -1;
    }
    public boolean inCheck() { return inCheck(side); }
    boolean inCheck(int color) { return attacked(Long.numberOfTrailingZeros(pieces[color * 6 + KING]), color ^ 1); }
    boolean attacked(int square, int by) { return attacked(square, by, all(), 0); }
    private boolean attacked(int s, int by, long occ, long removedPawn) {
        int b = by * 6;
        return (Attacks.PAWN[by ^ 1][s] & pieces[b] & ~removedPawn) != 0
                || (Attacks.KNIGHT[s] & pieces[b + KNIGHT]) != 0
                || (Attacks.KING[s] & pieces[b + KING]) != 0
                || (Attacks.bishop(s, occ) & (pieces[b + BISHOP] | pieces[b + QUEEN])) != 0
                || (Attacks.rook(s, occ) & (pieces[b + ROOK] | pieces[b + QUEEN])) != 0;
    }
    public void make(int move) {
        save(move);
        int oldEp = epFile(); hash ^= RIGHTS[castling] ^ (oldEp < 0 ? 0 : EP[oldEp]);
        int from = Move.from(move), to = Move.to(move), p = squares[from];
        int capturedSquare = (move & Move.EN_PASSANT) != 0 ? to + (side == 0 ? -8 : 8) : to;
        int captured = squares[capturedSquare]; undoCaptured[ply - 1] = captured;
        if (captured >= 0) remove(captured, capturedSquare);
        remove(p, from); put(Move.promotion(move) == 0 ? p : side * 6 + Move.promotion(move), to);
        if ((move & Move.CASTLE) != 0) {
            int rookFrom = to > from ? from + 3 : from - 4, rookTo = to > from ? from + 1 : from - 1;
            remove(side * 6 + ROOK, rookFrom); put(side * 6 + ROOK, rookTo);
        }
        castling &= CASTLE_MASK[from] & CASTLE_MASK[to];
        ep = p % 6 == PAWN && Math.abs(to - from) == 16 ? (from + to) / 2 : -1;
        halfmove = p % 6 == PAWN || captured >= 0 ? 0 : halfmove + 1;
        fullmove += side; side ^= 1; hash ^= TURN ^ RIGHTS[castling];
        int newEp = epFile(); if (newEp >= 0) hash ^= EP[newEp];
    }
    private void save(int move) {
        if (ply >= undoMove.length) throw new IllegalStateException("Position history exhausted");
        undoMove[ply] = move; undoRights[ply] = castling; undoEp[ply] = ep;
        undoHalf[ply] = halfmove; undoHash[ply] = hash; undoCaptured[ply] = -1; ply++;
    }
    void makeNull() {
        save(0); int file = epFile(); if (file >= 0) hash ^= EP[file];
        ep = -1; fullmove += side; side ^= 1; hash ^= TURN;
    }
    public void unmake() {
        int i = --ply, move = undoMove[i];
        side ^= 1; fullmove -= side;
        if (move != 0) {
            int from = Move.from(move), to = Move.to(move), p = squares[to];
            remove(p, to); put(Move.promotion(move) == 0 ? p : side * 6 + PAWN, from);
            if ((move & Move.CASTLE) != 0) {
                int rookFrom = to > from ? from + 3 : from - 4, rookTo = to > from ? from + 1 : from - 1;
                remove(side * 6 + ROOK, rookTo); put(side * 6 + ROOK, rookFrom);
            }
            if (undoCaptured[i] >= 0) put(undoCaptured[i], (move & Move.EN_PASSANT) != 0 ? to + (side == 0 ? -8 : 8) : to);
        }
        castling = undoRights[i]; ep = undoEp[i]; halfmove = undoHalf[i]; hash = undoHash[i];
    }
    boolean repetition() {
        int count = 1;
        for (int i = ply - 1; i >= Math.max(0, ply - halfmove); i--) {
            if (undoMove[i] == 0) break; // A null move is not a game move.
            if (undoHash[i] == hash && ++count >= 3) return true;
        }
        return false;
    }
    boolean hasNonPawnMaterial() { return (occupied[side] & ~(pieces[side * 6] | pieces[side * 6 + KING])) != 0; }
    boolean insufficientMaterial() {
        if ((pieces[0] | pieces[6] | pieces[ROOK] | pieces[6 + ROOK] | pieces[QUEEN] | pieces[6 + QUEEN]) != 0) return false;
        long minors = pieces[KNIGHT] | pieces[6 + KNIGHT] | pieces[BISHOP] | pieces[6 + BISHOP];
        if (Long.bitCount(minors) <= 1) return true;
        return (pieces[KNIGHT] | pieces[6 + KNIGHT]) == 0
                && ((minors & 0x55aa55aa55aa55aaL) == 0 || (minors & 0xaa55aa55aa55aa55L) == 0);
    }
    void legalMoves(MoveList list) {
        pseudoMoves(list);
        int legal = 0, color = side;
        for (int i = 0; i < list.size; i++) {
            int move = list.moves[i]; make(move);
            boolean ok = !inCheck(color); unmake();
            if (ok) list.moves[legal++] = move;
        }
        list.size = legal;
    }
    public int[] legalMoves() {
        MoveList list = new MoveList(); legalMoves(list); return Arrays.copyOf(list.moves, list.size);
    }
    private void pseudoMoves(MoveList list) {
        list.size = 0; long occ = all(), own = occupied[side], enemy = occupied[side ^ 1];
        long pawns = pieces[side * 6]; int step = side == 0 ? 8 : -8;
        while (pawns != 0) {
            int from = Long.numberOfTrailingZeros(pawns); pawns &= pawns - 1;
            int to = from + step;
            if (squares[to] < 0) {
                pawnMove(list, from, to, 0);
                if (from / 8 == (side == 0 ? 1 : 6) && squares[to + step] < 0) list.add(Move.of(from, to + step));
            }
            long targets = Attacks.PAWN[side][from] & enemy & ~pieces[(side ^ 1) * 6 + KING];
            while (targets != 0) {
                to = Long.numberOfTrailingZeros(targets); targets &= targets - 1; pawnMove(list, from, to, Move.CAPTURE);
            }
            if (ep >= 0 && squares[ep] < 0 && (Attacks.PAWN[side][from] & (1L << ep)) != 0
                    && squares[ep - step] == (side ^ 1) * 6 + PAWN)
                list.add(Move.of(from, ep) | Move.CAPTURE | Move.EN_PASSANT);
        }
        for (int type = KNIGHT; type <= KING; type++) {
            long bits = pieces[side * 6 + type];
            while (bits != 0) {
                int from = Long.numberOfTrailingZeros(bits); bits &= bits - 1;
                long targets = Attacks.piece(type, from, occ) & ~own & ~pieces[(side ^ 1) * 6 + KING];
                while (targets != 0) {
                    int to = Long.numberOfTrailingZeros(targets); targets &= targets - 1;
                    list.add(Move.of(from, to) | (squares[to] >= 0 ? Move.CAPTURE : 0));
                }
            }
        }
        int base = side == 0 ? 0 : 56, shift = side * 2, by = side ^ 1;
        if (squares[base + 4] == side * 6 + KING && !attacked(base + 4, by)) {
            if ((castling & (1 << shift)) != 0 && squares[base + 7] == side * 6 + ROOK
                    && (occ & (0x60L << base)) == 0 && !attacked(base + 5, by) && !attacked(base + 6, by))
                list.add(Move.of(base + 4, base + 6) | Move.CASTLE);
            if ((castling & (2 << shift)) != 0 && squares[base] == side * 6 + ROOK
                    && (occ & (0xeL << base)) == 0 && !attacked(base + 3, by) && !attacked(base + 2, by))
                list.add(Move.of(base + 4, base + 2) | Move.CASTLE);
        }
    }
    private void pawnMove(MoveList list, int from, int to, int flags) {
        if (to / 8 == 0 || to / 8 == 7) for (int p = QUEEN; p >= KNIGHT; p--) list.add(Move.of(from, to) | (p << 12) | flags);
        else list.add(Move.of(from, to) | flags);
    }
    public long perft(int depth) {
        if (depth < 0 || depth > 32) throw new IllegalArgumentException("Perft depth must be 0..32");
        MoveList[] lists = new MoveList[depth + 1];
        for (int i = 0; i <= depth; i++) lists[i] = new MoveList();
        return perft(depth, lists);
    }
    private long perft(int depth, MoveList[] lists) {
        if (depth == 0) return 1;
        MoveList list = lists[depth]; legalMoves(list);
        if (depth == 1) return list.size;
        long nodes = 0;
        for (int i = 0; i < list.size; i++) { make(list.moves[i]); nodes += perft(depth - 1, lists); unmake(); }
        return nodes;
    }
}
